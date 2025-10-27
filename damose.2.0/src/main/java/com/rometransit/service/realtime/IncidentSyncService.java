package com.rometransit.service.realtime;

import com.rometransit.data.repository.IncidentRepository;
import com.rometransit.model.dto.ArrivalPrediction;
import com.rometransit.model.entity.TransportIncident;
import com.rometransit.util.language.LanguageManager;
import com.rometransit.util.logging.Logger;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servizio di Sincronizzazione Ritardi e Incidenti
 *
 * Gestisce due categorie separate:
 * 1. RITARDI (arancione): Rilevati automaticamente dai trip updates GTFS
 *    - LOW: ritardi 5-10 minuti
 *    - MEDIUM: ritardi 10-15 minuti
 *    - HIGH: ritardi >15 minuti
 *
 * 2. INCIDENTI (rosso): Eventi eccezionali da altre fonti (es. API incidenti, feed news)
 *    - Da implementare in futuro con fonti dati esterne
 *
 * I ritardi NON sono incidenti - sono segnalazioni di servizio normale ma in ritardo.
 * Gli incidenti sono eventi straordinari (guasti, manifestazioni, incidenti stradali, etc).
 */
public class IncidentSyncService {

    private static final Logger logger = Logger.getLogger(IncidentSyncService.class);

    // Soglie ritardi (in minuti)
    private static final int LOW_DELAY_THRESHOLD = 5;      // 5-10 minuti
    private static final int MEDIUM_DELAY_THRESHOLD = 10;  // 10-15 minuti
    private static final int HIGH_DELAY_THRESHOLD = 15;    // >15 minuti

    // Soglie per rilevamento ritardi diffusi su una linea
    private static final int MIN_AFFECTED_TRIPS_FOR_ROUTE_DELAY = 3;
    private static final double ROUTE_DELAY_PERCENTAGE = 0.5; // 50% delle corse in ritardo

    private final IncidentRepository incidentRepository;

    // Traccia ritardi attivi (NON incidenti) per evitare duplicati
    private final Map<String, TransportIncident> activeDelays = new HashMap<>();

    public IncidentSyncService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;

        // Migra vecchi dati DELAY -> RITARDO (una tantum)
        migrateOldData();

        loadActiveDelays();
    }

    /**
     * Migra vecchi incidenti con type="DELAY"/"SEVERE_DELAY" a "RITARDO"
     */
    private void migrateOldData() {
        try {
            int migrated = incidentRepository.migrateOldDelaysToRitardi();
            if (migrated > 0) {
                logger.info("Migrati " + migrated + " vecchi record da DELAY/SEVERE_DELAY a RITARDO");
            }
        } catch (Exception e) {
            logger.warn("Errore durante la migrazione dei dati: " + e.getMessage());
        }
    }

    /**
     * Sincronizza ritardi dai trip updates GTFS
     * NOTA: Questo metodo crea segnalazioni di RITARDO (arancione), NON incidenti (rosso)
     *
     * @param predictions Lista di previsioni arrivo con informazioni sui ritardi
     * @return Numero di ritardi rilevati/aggiornati
     */
    public int syncIncidentsFromTripUpdates(List<ArrivalPrediction> predictions) {
        logger.info("Sincronizzazione ritardi da " + predictions.size() + " trip updates...");

        // Filtra previsioni con ritardi significativi (≥5 minuti)
        List<ArrivalPrediction> delayedPredictions = predictions.stream()
                .filter(p -> p.getDelayMinutes() >= LOW_DELAY_THRESHOLD)
                .collect(Collectors.toList());

        if (delayedPredictions.isEmpty()) {
            logger.info("Nessun ritardo rilevato, risoluzione ritardi attivi");
            resolveAllActiveDelays();
            return 0;
        }

        logger.info("Trovate " + delayedPredictions.size() + " previsioni con ritardi >= " +
                   LOW_DELAY_THRESHOLD + " minuti");

        // Raggruppa per linea per rilevare ritardi diffusi
        Map<String, List<ArrivalPrediction>> delaysByRoute = delayedPredictions.stream()
                .filter(p -> p.getRouteId() != null)
                .collect(Collectors.groupingBy(ArrivalPrediction::getRouteId));

        int delaysCreated = 0;
        int delaysUpdated = 0;

        // Rileva ritardi diffusi su una linea (≥3 corse in ritardo)
        for (Map.Entry<String, List<ArrivalPrediction>> entry : delaysByRoute.entrySet()) {
            String routeId = entry.getKey();
            List<ArrivalPrediction> routeDelays = entry.getValue();

            // Verifica se abbastanza corse sono in ritardo per creare una segnalazione di linea
            if (routeDelays.size() >= MIN_AFFECTED_TRIPS_FOR_ROUTE_DELAY) {
                TransportIncident delay = detectOrUpdateRouteDelay(routeId, routeDelays);
                if (delay != null) {
                    if (activeDelays.containsKey(delay.getId())) {
                        delaysUpdated++;
                    } else {
                        delaysCreated++;
                    }
                }
            }
        }

        // Risolvi ritardi non più presenti
        resolveResolvedDelays(delaysByRoute.keySet());

        logger.info("Sincronizzazione ritardi completata: " + delaysCreated + " creati, " +
                   delaysUpdated + " aggiornati, " + activeDelays.size() + " attivi");

        return activeDelays.size();
    }

    /**
     * Rileva o aggiorna ritardo diffuso su una linea
     */
    private TransportIncident detectOrUpdateRouteDelay(String routeId,
                                                       List<ArrivalPrediction> delays) {
        // Calcola ritardo medio
        double avgDelay = delays.stream()
                .mapToInt(ArrivalPrediction::getDelayMinutes)
                .average()
                .orElse(0);

        // Determina severità in base al ritardo medio
        TransportIncident.Severity severity;
        if (avgDelay >= HIGH_DELAY_THRESHOLD) {
            severity = TransportIncident.Severity.HIGH;      // >15 min
        } else if (avgDelay >= MEDIUM_DELAY_THRESHOLD) {
            severity = TransportIncident.Severity.MEDIUM;    // 10-15 min
        } else {
            severity = TransportIncident.Severity.LOW;       // 5-10 min
        }

        // Genera ID ritardo basato sulla linea (evita duplicati)
        String delayId = "RITARDO_LINEA_" + routeId;

        // Verifica se il ritardo esiste già
        TransportIncident delay = activeDelays.get(delayId);

        if (delay != null) {
            // Aggiorna ritardo esistente
            delay.setDescription(String.format(
                "Ritardo medio di %.0f minuti sulla linea %s (%d corse interessate)",
                avgDelay, routeId, delays.size()
            ));
            delay.setSeverity(severity);

            logger.debug("Aggiornato ritardo " + delayId + ": ritardo medio " + avgDelay + " min");

        } else {
            // Crea nuovo ritardo
            delay = new TransportIncident();
            delay.setId(delayId);
            delay.setType("RITARDO");  // RITARDO (arancione), non INCIDENTE (rosso)
            delay.setSeverity(severity);
            delay.setLocation(LanguageManager.getInstance().getString("incident.routePrefix") + " " + routeId);
            delay.setDescription(String.format(
                LanguageManager.getInstance().getString("incident.delayDescription"),
                routeId, avgDelay, delays.size()
            ));
            delay.setStartTime(LocalDateTime.now());
            delay.setActive(true);

            // Aggiungi linea interessata
            List<String> affectedRoutes = new ArrayList<>();
            affectedRoutes.add(routeId);
            delay.setAffectedRoutes(affectedRoutes);

            logger.info("Creato nuovo ritardo linea " + delayId + ": ritardo medio " +
                       avgDelay + " min, severità " + severity);
        }

        // Salva nel database
        incidentRepository.save(delay);
        activeDelays.put(delayId, delay);

        return delay;
    }


    /**
     * Risolvi ritardi non più presenti nei dati correnti
     */
    private void resolveResolvedDelays(Set<String> currentAffectedRoutes) {
        List<String> toResolve = new ArrayList<>();

        for (Map.Entry<String, TransportIncident> entry : activeDelays.entrySet()) {
            String delayId = entry.getKey();
            TransportIncident delay = entry.getValue();

            // Verifica se la linea è ancora in ritardo
            boolean stillDelayed = false;
            if (delay.getAffectedRoutes() != null) {
                for (String routeId : delay.getAffectedRoutes()) {
                    if (currentAffectedRoutes.contains(routeId)) {
                        stillDelayed = true;
                        break;
                    }
                }
            }

            // Se non è più in ritardo, risolvi
            if (!stillDelayed) {
                toResolve.add(delayId);
            }
        }

        // Risolvi ritardi
        for (String delayId : toResolve) {
            resolveDelay(delayId);
        }
    }

    /**
     * Risolvi tutti i ritardi attivi (chiamato quando non si rilevano ritardi)
     */
    private void resolveAllActiveDelays() {
        if (activeDelays.isEmpty()) {
            return;
        }

        logger.info("Nessun ritardo rilevato, risoluzione di tutti i " + activeDelays.size() + " ritardi attivi");

        List<String> toResolve = new ArrayList<>(activeDelays.keySet());
        for (String delayId : toResolve) {
            resolveDelay(delayId);
        }
    }

    /**
     * Risolvi (segna come inattivo) un ritardo
     */
    private void resolveDelay(String delayId) {
        TransportIncident delay = activeDelays.get(delayId);
        if (delay == null) {
            return;
        }

        logger.info("Risoluzione ritardo " + delayId + ": " + delay.getLocation());

        delay.setActive(false);
        delay.setEndTime(LocalDateTime.now());
        incidentRepository.save(delay);

        activeDelays.remove(delayId);
    }

    /**
     * Carica ritardi attivi dal database all'avvio
     */
    private void loadActiveDelays() {
        try {
            List<TransportIncident> delays = incidentRepository.findActiveIncidents();
            // Filtra solo i ritardi (type="RITARDO"), non incidenti
            for (TransportIncident delay : delays) {
                if ("RITARDO".equals(delay.getType())) {
                    activeDelays.put(delay.getId(), delay);
                }
            }
            logger.info("Caricati " + activeDelays.size() + " ritardi attivi dal database");
        } catch (Exception e) {
            logger.warn("Errore nel caricamento dei ritardi attivi: " + e.getMessage());
        }
    }

    /**
     * Ottieni conteggio ritardi attivi per severità
     */
    public Map<TransportIncident.Severity, Long> getIncidentCountBySeverity() {
        return activeDelays.values().stream()
                .collect(Collectors.groupingBy(
                    TransportIncident::getSeverity,
                    Collectors.counting()
                ));
    }

    /**
     * Ottieni tutti i ritardi attivi
     * NOTA: Questo metodo restituisce solo RITARDI (arancione), non incidenti (rosso)
     */
    public List<TransportIncident> getActiveIncidents() {
        return new ArrayList<>(activeDelays.values());
    }

    /**
     * Cancella tutti i ritardi (per test)
     */
    public void clearAllIncidents() {
        logger.info("Cancellazione di tutti i ritardi");
        for (String delayId : new ArrayList<>(activeDelays.keySet())) {
            resolveDelay(delayId);
        }
    }



    }

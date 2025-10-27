package com.rometransit.ui.listener;

import com.rometransit.model.dto.VehiclePosition;
import java.util.List;

/**
 * Listener per aggiornamenti posizioni veicoli real-time
 * Riceve notifiche quando i dati GTFS Realtime vengono aggiornati (ogni 30s)
 */
public interface VehicleUpdateListener {

    /**
     * Chiamato quando le posizioni veicoli sono aggiornate
     * @param positions Lista aggiornata di tutte le posizioni veicoli
     */
    void onVehiclesUpdated(List<VehiclePosition> positions);

    /**
     * Chiamato quando un singolo veicolo è aggiornato
     * @param position Posizione aggiornata del veicolo
     */
    default void onVehicleUpdated(VehiclePosition position) {
        // Implementazione opzionale per aggiornamenti singoli
    }

    /**
     * Chiamato quando l'aggiornamento fallisce
     * @param error Errore che ha causato il fallimento
     */
    default void onUpdateFailed(Exception error) {
        System.err.println("⚠️ Vehicle update failed: " + error.getMessage());
    }
}

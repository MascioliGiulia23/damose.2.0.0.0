package com.rometransit.model.entity;

import java.time.LocalDateTime;

public class SearchHistory {
    private String searchId;
    private String userId;
    private String query;
    private SearchType searchType;
    private String resultId;
    private String resultName;
    private LocalDateTime searchTime;
    private int resultCount;
    private boolean wasSuccessful;

    public SearchHistory() {
        this.searchTime = LocalDateTime.now();
    }

    public SearchHistory(String userId, String query, SearchType searchType) {
        this();
        this.searchId = generateSearchId();
        this.userId = userId;
        this.query = query;
        this.searchType = searchType;
    }

    private String generateSearchId() {
        return "search_" + System.currentTimeMillis() + "_" + userId;
    }

    // Getters and Setters
    public String getSearchId() {
        return searchId;
    }

    public void setSearchId(String searchId) {
        this.searchId = searchId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String resultId) {
        this.resultId = resultId;
    }

    public String getResultName() {
        return resultName;
    }

    public void setResultName(String resultName) {
        this.resultName = resultName;
    }

    public LocalDateTime getSearchTime() {
        return searchTime;
    }

    public void setSearchTime(LocalDateTime searchTime) {
        this.searchTime = searchTime;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }

    public boolean isWasSuccessful() {
        return wasSuccessful;
    }

    public void setWasSuccessful(boolean wasSuccessful) {
        this.wasSuccessful = wasSuccessful;
    }

    public String getDisplayText() {
        return searchType.getDisplayName() + ": " + query;
    }

    @Override
    public String toString() {
        return "SearchHistory{" +
                "searchId='" + searchId + '\'' +
                ", userId='" + userId + '\'' +
                ", query='" + query + '\'' +
                ", searchType=" + searchType +
                ", resultCount=" + resultCount +
                ", searchTime=" + searchTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SearchHistory that = (SearchHistory) o;

        return searchId != null ? searchId.equals(that.searchId) : that.searchId == null;
    }

    @Override
    public int hashCode() {
        return searchId != null ? searchId.hashCode() : 0;
    }

    public enum SearchType {
        STOP("Stop Search"),
        ROUTE("Route Search"),
        ADDRESS("Address Search"),
        NEARBY("Nearby Search");

        private final String displayName;

        SearchType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
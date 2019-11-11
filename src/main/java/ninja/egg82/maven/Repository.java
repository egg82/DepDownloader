package ninja.egg82.maven;

import java.util.*;

public class Repository {
    String url;
    public String getURL() { return url; }

    private Set<String> proxies = new LinkedHashSet<>();
    public Set<String> getProxies() { return Collections.unmodifiableSet(proxies); }

    private int hashCode = -1;

    private Repository(String url) { this.url = url; }

    private Repository build() {
        hashCode = Objects.hash(url, proxies);
        return this;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Repository)) return false;
        Repository that = (Repository) o;
        return url.equals(that.url) &&
                proxies.equals(that.proxies);
    }

    public int hashCode() { return hashCode; }

    public static Builder builder(String url) { return new Builder(url); }

    public static class Builder {
        private final Repository result;

        private Builder(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url cannot be null or empty.");
            }

            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }

            result = new Repository(url);
        }

        public Builder addProxy(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url cannot be null or empty.");
            }

            if (url.charAt(url.length() - 1) != '/') {
                url = url + "/";
            }

            result.proxies.add(url);
            return this;
        }

        public Repository build() {
            return result.build();
        }
    }
}

package io.github.martinwitt.urlcleaner;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class UrlController {

    private static final Set<String> TRACKING_PARAMS =
            Set.of(
                    "utm_source",
                    "utm_medium",
                    "utm_campaign",
                    "utm_term",
                    "utm_content",
                    "rcm",
                    "fbclid",
                    "gclid",
                    "msclkid",
                    "ttclid",
                    "yclid",
                    "twclid");

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/clean")
    public String cleanUrl(@RequestParam("url") String url, RedirectAttributes redirectAttributes) {
        CleaningResult result = cleanTrackingParams(url);

        redirectAttributes.addFlashAttribute("originalUrl", url);
        redirectAttributes.addFlashAttribute("cleanedUrl", result.cleanedUrl());
        redirectAttributes.addFlashAttribute("paramsRemoved", result.paramsRemoved());
        redirectAttributes.addFlashAttribute("lengthReduction", result.lengthReduction());

        return "redirect:/";
    }

    private CleaningResult cleanTrackingParams(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();

            if (query == null || query.isEmpty()) {
                return new CleaningResult(url, 0, 0.0);
            }

            var cleanQueryResult = buildCleanQuery(query);
            String cleanedUrl = buildCleanUrl(uri, cleanQueryResult.query());
            double lengthReduction = calculateLengthReduction(url, cleanedUrl);

            return new CleaningResult(cleanedUrl, cleanQueryResult.removedCount(), lengthReduction);

        } catch (URISyntaxException e) {
            return new CleaningResult(url, 0, 0.0);
        }
    }

    private QueryResult buildCleanQuery(String query) {
        String[] params = query.split("&");
        StringBuilder cleanQuery = new StringBuilder();
        int removedCount = 0;

        for (String param : params) {
            String[] keyValue = param.split("=", 2);

            if (keyValue.length == 2 && !TRACKING_PARAMS.contains(keyValue[0])) {
                if (!cleanQuery.isEmpty()) {
                    cleanQuery.append("&");
                }
                cleanQuery.append(param);
            } else {
                removedCount++;
            }
        }

        return new QueryResult(cleanQuery.toString(), removedCount);
    }

    private String buildCleanUrl(URI uri, String cleanQuery) throws URISyntaxException {
        return new URI(
                        uri.getScheme(),
                        uri.getAuthority(),
                        uri.getPath(),
                        cleanQuery.isEmpty() ? null : cleanQuery,
                        uri.getFragment())
                .toString();
    }

    private double calculateLengthReduction(String originalUrl, String cleanedUrl) {
        if (originalUrl.isEmpty()) {
            return 0.0;
        }
        return ((double) (originalUrl.length() - cleanedUrl.length()) / originalUrl.length()) * 100;
    }

    private record CleaningResult(String cleanedUrl, int paramsRemoved, double lengthReduction) {}

    private record QueryResult(String query, int removedCount) {}
}

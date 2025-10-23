package ua.kpi.ia33.shellweb.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.kpi.ia33.shellweb.domain.SearchQuery;
import ua.kpi.ia33.shellweb.domain.SearchResult;
import ua.kpi.ia33.shellweb.domain.User;
import ua.kpi.ia33.shellweb.repo.SearchQueryRepository;
import ua.kpi.ia33.shellweb.repo.SearchResultRepository;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class SearchService {
    private final SearchQueryRepository queries;
    private final SearchResultRepository results;

    public SearchService(SearchQueryRepository queries, SearchResultRepository results) {
        this.queries = queries;
        this.results = results;
    }

    @Transactional
    public SearchQuery createQuery(User user, String nameMask, String ext) {
        SearchQuery q = new SearchQuery();
        q.setUser(user);
        q.setNameMask(nameMask);
        q.setExt(ext);
        q.setCreatedAt(Instant.now());
        q = queries.save(q);

        // naive local FS search (demo) - searches in user's home directory
        results.deleteByQuery(q);
        List<SearchResult> batch = new ArrayList<>();
        File home = new File(System.getProperty("user.home"));
        Pattern nameRe = nameMask == null || nameMask.isBlank()
                ? Pattern.compile(".*")
                : Pattern.compile(nameMask.replace("*", ".*"), Pattern.CASE_INSENSITIVE);
        String extNorm = (ext == null) ? "" : ext.trim().toLowerCase();
        walk(home, q, nameRe, extNorm, batch);
        results.saveAll(batch);
        return q;
    }

    private void walk(File root, SearchQuery q, Pattern nameRe, String ext, List<SearchResult> out) {
        File[] list = root.listFiles();
        if (list == null) return;

        for (File f : list) {
            if (f.isDirectory()) {
                // якщо задано розширення – каталоги НЕ додаємо до результатів
                if (ext.isEmpty() && nameRe.matcher(f.getName()).matches()) {
                    SearchResult r = new SearchResult();
                    r.setQuery(q);
                    r.setPath(f.getAbsolutePath());
                    r.setName(f.getName());
                    r.setType("dir");
                    out.add(r);
                }
                // Рекурсія
                walk(f, q, nameRe, ext, out);

            } else { // файл
                String nm = f.getName();
                boolean ok = nameRe.matcher(nm).matches();

                if (!ext.isEmpty()) {
                    int dot = nm.lastIndexOf('.');
                    String e = dot >= 0 ? nm.substring(dot + 1).toLowerCase() : "";
                    ok = ok && e.equals(ext);
                }

                if (ok) {
                    SearchResult r = new SearchResult();
                    r.setQuery(q);
                    r.setPath(f.getAbsolutePath());
                    r.setName(nm);
                    r.setType("file");
                    out.add(r);
                }
            }

            // щоб не “зрізати” файли через каталоги, ліміт рахуємо лише після добору файлів
            if (out.size() >= 1000) return;
        }
    }


    public List<SearchQuery> listQueries(User user) {
        return queries.findByUserOrderByCreatedAtDesc(user);
    }

    public SearchQuery findQueryByIdForUser(Long id, User user) {
        return queries.findById(id)
                .filter(q -> q.getUser().getId().equals(user.getId()))
                .orElse(null);
    }


    public List<SearchResult> resultsFor(SearchQuery q) {
        return results.findByQuery(q);
    }
}

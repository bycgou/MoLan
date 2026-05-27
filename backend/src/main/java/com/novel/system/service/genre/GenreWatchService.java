package com.novel.system.service.genre;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class GenreWatchService {

    private final GenreService genreService;

    private WatchService watchService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void start() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource resource = resolver.getResource("classpath:genres");
            Path dir = resource.getFile().toPath().toAbsolutePath();

            watchService = FileSystems.getDefault().newWatchService();
            dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            log.info("GenreWatchService watching directory: {}", dir);

            executor.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        WatchKey key = watchService.take();
                        // Debounce: wait 500ms for more events
                        scheduler.schedule(() -> {
                            log.info("Genre files changed, reloading...");
                            genreService.reload();
                        }, 500, TimeUnit.MILLISECONDS);
                        key.reset();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        } catch (Exception e) {
            log.warn("GenreWatchService failed to start (hot reload disabled): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
        scheduler.shutdownNow();
        if (watchService != null) {
            try {
                watchService.close();
            } catch (Exception ignored) {}
        }
    }
}

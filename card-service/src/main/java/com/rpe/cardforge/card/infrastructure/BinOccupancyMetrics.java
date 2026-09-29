package com.rpe.cardforge.card.infrastructure;

import com.rpe.cardforge.card.domain.BinOccupancy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publica {@code cardforge_bin_occupancy_ratio{bin}} e loga {@code ALERT} quando um BIN chega a 70%
 * da faixa. Cartões cancelados contam: o PAN continua ocupado.
 */
@Component
public class BinOccupancyMetrics {

  private static final Logger log = LoggerFactory.getLogger(BinOccupancyMetrics.class);

  private final JdbcClient jdbc;
  private final MultiGauge occupancy;

  BinOccupancyMetrics(JdbcClient jdbc, MeterRegistry registry) {
    this.jdbc = jdbc;
    this.occupancy =
        MultiGauge.builder("cardforge.bin.occupancy.ratio")
            .description("Share of the PAN space of each BIN already used by issued cards")
            .register(registry);
  }

  @Scheduled(
      initialDelayString = "${cardforge.metrics.bin-occupancy-refresh-millis:60000}",
      fixedDelayString = "${cardforge.metrics.bin-occupancy-refresh-millis:60000}")
  public void refresh() {
    List<BinOccupancy> bins =
        jdbc.sql("SELECT bin, count(*) AS cards FROM cards WHERE bin IS NOT NULL GROUP BY bin")
            .query((rs, row) -> new BinOccupancy(rs.getString("bin"), rs.getLong("cards")))
            .list();
    occupancy.register(
        bins.stream().map(b -> MultiGauge.Row.of(Tags.of("bin", b.bin()), b.ratio())).toList(),
        true);
    bins.stream()
        .filter(BinOccupancy::requiresAlert)
        .forEach(
            b ->
                log.error(
                    "ALERT BIN occupancy: BIN {} at {}% of its PAN space",
                    b.bin(), String.format("%.1f", b.ratio() * 100)));
  }
}

package co.soundpass.controlador;

import co.soundpass.fachada.LogFachada;
import co.soundpass.vista.LogDto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogFachada logs;

    public LogController(LogFachada logs) {
        this.logs = logs;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<LogDto> consultar(
            @RequestParam(required = false) @Pattern(regexp = "ERROR|WARN|INFO") String nivel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite) {
        return logs.consultar(nivel, desde, hasta, limite);
    }
}

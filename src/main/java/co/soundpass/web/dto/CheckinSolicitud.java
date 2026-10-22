package co.soundpass.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckinSolicitud(@NotNull UUID codigo) {
}
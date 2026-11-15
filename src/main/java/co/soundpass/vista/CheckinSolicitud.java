package co.soundpass.vista;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckinSolicitud(@NotNull UUID codigo) {
}

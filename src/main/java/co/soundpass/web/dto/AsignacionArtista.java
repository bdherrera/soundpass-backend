package co.soundpass.web.dto;

import jakarta.validation.constraints.Min;

/** Si no se indica el orden de presentación, se usa 1. */
public record AsignacionArtista(@Min(1) Integer ordenPresentacion) {
}
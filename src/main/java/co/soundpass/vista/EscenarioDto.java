package co.soundpass.vista;

import co.soundpass.modelo.Escenario;

public record EscenarioDto(Integer idEscenario, String nombre, String ciudad, String direccion, int capacidad) {

    public static EscenarioDto de(Escenario e) {
        return new EscenarioDto(e.getId(), e.getNombre(), e.getCiudad(), e.getDireccion(), e.getCapacidad());
    }
}

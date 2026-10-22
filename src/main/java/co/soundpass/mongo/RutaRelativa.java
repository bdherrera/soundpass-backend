package co.soundpass.mongo;

/** Las imágenes se guardan como ruta relativa en texto: sin "/" inicial, sin esquema (http:), sin "..". */
public final class RutaRelativa {

    public static final String PATRON = "^(?!/)(?!\\\\)(?!.*\\.\\.)(?!.*:).+$";

    private RutaRelativa() {
    }
}
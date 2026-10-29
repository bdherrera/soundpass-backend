package co.soundpass.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import co.soundpass.modelo.Rol;
import co.soundpass.modelo.Usuario;
import co.soundpass.repositorio.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/** Utilidades de pruebas de integración: crear usuarios y obtener tokens reales por la API. */
public class ClienteApi {

    public static final String CLAVE = "Clave-segura-1";

    private final MockMvc mvc;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final ObjectMapper mapper;

    public ClienteApi(MockMvc mvc, UsuarioRepository usuarios, PasswordEncoder encoder, ObjectMapper mapper) {
        this.mvc = mvc;
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.mapper = mapper;
    }

    public Usuario crearUsuario(Rol rol) {
        Usuario u = new Usuario();
        u.setNombre("Usuario " + rol);
        u.setCorreo(UUID.randomUUID() + "@prueba.co");
        u.setContrasenaHash(encoder.encode(CLAVE));
        u.setRol(rol);
        return usuarios.saveAndFlush(u);
    }

    /** Devuelve la cabecera "Bearer ..." tras iniciar sesión con el usuario dado. */
    public String bearer(Usuario usuario) throws Exception {
        String cuerpo = mapper.writeValueAsString(Map.of("correo", usuario.getCorreo(), "contrasena", CLAVE));
        String respuesta = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = mapper.readTree(respuesta);
        return "Bearer " + json.get("token").asText();
    }

    public String bearerDe(Rol rol) throws Exception {
        return bearer(crearUsuario(rol));
    }
}

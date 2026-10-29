package co.soundpass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.soundpass.modelo.Rol;
import co.soundpass.modelo.Usuario;
import co.soundpass.repositorio.UsuarioRepository;
import co.soundpass.servicio.AdminInicial;
import co.soundpass.soporte.ClienteApi;
import co.soundpass.soporte.PruebaController;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(PruebaController.class)
class AutenticacionTest {

    @Autowired MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper mapper;
    ClienteApi cliente;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    @org.junit.jupiter.api.BeforeEach
    void preparar() {
        cliente = new ClienteApi(mvc, usuarios, encoder, mapper);
    }

    private static String registro(String correo, String clave) {
        return "{\"nombre\":\"Ana\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}";
    }

    private static String login(String correo, String clave) {
        return "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}";
    }

    @Test
    void registroCorrectoCreaUsuarioSinExponerElHash() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        MvcResult r = mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro(correo, "Clave-segura-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andReturn();

        String cuerpo = r.getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("hash").doesNotContain("contrasena").doesNotContain("$2");
        Usuario guardado = usuarios.findByCorreo(correo).orElseThrow();
        assertThat(guardado.getContrasenaHash()).startsWith("$2").isNotEqualTo("Clave-segura-1");
    }

    @Test
    void correoDuplicadoDevuelve409() throws Exception {
        String correo = UUID.randomUUID() + "@prueba.co";
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                .content(registro(correo, "Clave-segura-1"))).andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro(correo.toUpperCase(), "Otra-clave-2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DUPLICADO"))
                .andExpect(jsonPath("$.estado").value(409));
    }

    @Test
    void registroInvalidoDevuelve422ConDetalles() throws Exception {
        mvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content(registro("no-es-correo", "corta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.ruta").value("/api/v1/auth/registro"))
                .andExpect(jsonPath("$.fecha").exists())
                .andExpect(jsonPath("$.detalles.length()").value(2));
    }

    @Test
    void cuerpoIlegibleDevuelve422() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"));
    }

    @Test
    void loginCorrectoDevuelveTokenYUsuario() throws Exception {
        Usuario u = cliente.crearUsuario(Rol.USUARIO);

        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(u.getCorreo(), ClienteApi.CLAVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").exists())
                .andExpect(jsonPath("$.usuario.correo").value(u.getCorreo()))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("hash").doesNotContain("$2");
    }

    @Test
    void loginIncorrectoDevuelve401() throws Exception {
        Usuario u = cliente.crearUsuario(Rol.USUARIO);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(u.getCorreo(), "equivocada-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login("nadie@prueba.co", "equivocada-123")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void usuarioInactivoNoPuedeIniciarSesion() throws Exception {
        Usuario u = cliente.crearUsuario(Rol.USUARIO);
        u.setActivo(false);
        usuarios.saveAndFlush(u);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(login(u.getCorreo(), ClienteApi.CLAVE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaSinTokenDevuelve401ConErrorEstandar() throws Exception {
        mvc.perform(get("/api/v1/prueba/protegido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.estado").value(401))
                .andExpect(jsonPath("$.ruta").value("/api/v1/prueba/protegido"));
    }

    @Test
    void rutaProtegidaConTokenInvalidoDevuelve401() throws Exception {
        mvc.perform(get("/api/v1/prueba/protegido").header("Authorization", "Bearer token.falso.xyz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rutaProtegidaConTokenValidoDevuelve200() throws Exception {
        mvc.perform(get("/api/v1/prueba/protegido").header("Authorization", cliente.bearerDe(Rol.USUARIO)))
                .andExpect(status().isOk());
    }

    @Test
    void endpointDeAdminRechazaAUsuarioYAceptaAAdmin() throws Exception {
        mvc.perform(get("/api/v1/prueba/admin").header("Authorization", cliente.bearerDe(Rol.USUARIO)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(get("/api/v1/prueba/admin").header("Authorization", cliente.bearerDe(Rol.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void adminInicialSeCreaSoloSiHayVariables() {
        String correo = UUID.randomUUID() + "@prueba.co";

        new AdminInicial(usuarios, encoder, "", "").run(null);
        assertThat(usuarios.existsByCorreo(correo)).isFalse();

        AdminInicial admin = new AdminInicial(usuarios, encoder, correo.toUpperCase(), "Clave-admin-1");
        admin.run(null);
        admin.run(null);
        Usuario creado = usuarios.findByCorreo(correo).orElseThrow();
        assertThat(creado.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(encoder.matches("Clave-admin-1", creado.getContrasenaHash())).isTrue();
    }
}

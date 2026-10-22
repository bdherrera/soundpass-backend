package co.soundpass.servicio;

import co.soundpass.dominio.Rol;
import co.soundpass.dominio.Usuario;
import co.soundpass.repositorio.UsuarioRepository;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea un ADMIN al arrancar solo si ADMIN_CORREO y ADMIN_CONTRASENA están definidos y el correo no existe. */
@Component
public class AdminInicial implements ApplicationRunner {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String correo;
    private final String contrasena;

    public AdminInicial(UsuarioRepository usuarios, PasswordEncoder encoder,
                        @Value("${soundpass.admin.correo:}") String correo,
                        @Value("${soundpass.admin.contrasena:}") String contrasena) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.correo = correo;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (correo.isBlank() || contrasena.isBlank()) {
            return;
        }
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByCorreo(normalizado)) {
            return;
        }
        Usuario admin = new Usuario();
        admin.setNombre("Administrador");
        admin.setCorreo(normalizado);
        admin.setContrasenaHash(encoder.encode(contrasena));
        admin.setRol(Rol.ADMIN);
        usuarios.save(admin);
    }
}
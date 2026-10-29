package co.soundpass.servicio.impl;

import co.soundpass.servicio.AuthServicio;
import co.soundpass.error.ApiException;
import co.soundpass.error.CodigoError;
import co.soundpass.modelo.Rol;
import co.soundpass.modelo.Usuario;
import co.soundpass.repositorio.UsuarioRepository;
import co.soundpass.seguridad.JwtServicio;
import co.soundpass.vista.LoginSolicitud;
import co.soundpass.vista.RegistroSolicitud;
import co.soundpass.vista.TokenRespuesta;
import co.soundpass.vista.UsuarioDto;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServicioImpl implements AuthServicio {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtServicio jwt;

    public AuthServicioImpl(UsuarioRepository usuarios, PasswordEncoder encoder, JwtServicio jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    @Override
    public UsuarioDto registrar(RegistroSolicitud solicitud) {
        String correo = normalizar(solicitud.correo());
        if (usuarios.existsByCorreo(correo)) {
            throw ApiException.conflicto(CodigoError.DUPLICADO, "El correo ya está registrado");
        }
        Usuario usuario = new Usuario();
        usuario.setNombre(solicitud.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(encoder.encode(solicitud.contrasena()));
        usuario.setRol(Rol.USUARIO);
        return UsuarioDto.de(usuarios.save(usuario));
    }

    @Transactional(readOnly = true)
    @Override
    public TokenRespuesta iniciarSesion(LoginSolicitud solicitud) {
        Usuario usuario = usuarios.findByCorreo(normalizar(solicitud.correo()))
                .filter(Usuario::isActivo)
                .filter(u -> encoder.matches(solicitud.contrasena(), u.getContrasenaHash()))
                .orElseThrow(() -> ApiException.noAutenticado("Correo o contraseña incorrectos"));
        JwtServicio.Emitido emitido = jwt.emitir(usuario);
        return new TokenRespuesta(emitido.token(), "Bearer", emitido.expiraEn(), UsuarioDto.de(usuario));
    }

    private static String normalizar(String correo) {
        return correo.trim().toLowerCase(Locale.ROOT);
    }
}

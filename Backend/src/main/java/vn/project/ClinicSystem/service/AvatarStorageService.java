package vn.project.ClinicSystem.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "webp");

    private final Path rootLocation;
    private final String publicUrlPrefix;

    public AvatarStorageService(
            @Value("${clinicsystem.storage.avatars-dir:uploads/avatars}") String avatarsDir,
            @Value("${clinicsystem.storage.avatars-public-prefix:/uploads/avatars}") String publicUrlPrefix) {
        this.rootLocation = Paths.get(avatarsDir).toAbsolutePath().normalize();
        this.publicUrlPrefix = publicUrlPrefix.endsWith("/")
                ? publicUrlPrefix.substring(0, publicUrlPrefix.length() - 1)
                : publicUrlPrefix;
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể tạo thư mục lưu trữ ảnh đại diện", e);
        }
    }

    public String store(Long userId, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Tệp ảnh không hợp lệ");
        }
        if (file.getSize() > 5 * 1024 * 1024L) {
            throw new IllegalArgumentException("Ảnh đại diện vượt quá dung lượng cho phép (tối đa 5MB)");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        validateExtension(extension);

        String filename = "avatar-" + userId + "-" + System.currentTimeMillis() + "." + extension;
        Path destination = rootLocation.resolve(filename);
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        return publicUrlPrefix + "/" + filename;
    }

    public void deleteByUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        String normalized = avatarUrl.replace("\\", "/");
        if (!normalized.contains(publicUrlPrefix)) {
            return;
        }
        int idx = normalized.lastIndexOf('/');
        String filename = idx >= 0 ? normalized.substring(idx + 1) : normalized;
        if (filename.isBlank()) {
            return;
        }
        Path target = rootLocation.resolve(filename).normalize();
        if (!target.startsWith(rootLocation)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase();
    }

    private void validateExtension(String extension) {
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Định dạng ảnh không được hỗ trợ. Vui lòng chọn PNG, JPG, JPEG, GIF hoặc WEBP.");
        }
    }
}

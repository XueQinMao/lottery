package com.my.project.application.feature.support;

import cn.hutool.crypto.digest.DigestUtil;
import com.my.project.llm.bo.ZoneTrendAnalysisBo.ImageInfo;
import com.my.project.llm.bo.ZoneTrendImage;
import org.springframework.util.Assert;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class ZoneTrendImages {
    private static final String PREFIX = "data:image/png;base64,";
    private static final List<String> CODES = List.of("counts", "ratios-1", "ratios-2", "ratios-3", "ratios-4");
    private ZoneTrendImages() { }

    public static List<ImageInfo> validate(List<ZoneTrendImage> images) {
        Assert.isTrue(images != null && images.size() == CODES.size(), "需要1张三区个数图和4张三区比图");
        var result = new ArrayList<ImageInfo>();
        for (int index = 0; index < images.size(); index++) {
            var image = images.get(index);
            Assert.isTrue(image != null && CODES.get(index).equals(image.code()), "走势图缺失、重复或顺序错误");
            result.add(validate(image, 1200, "counts".equals(image.code()) ? 880 : 1700));
        }
        return List.copyOf(result);
    }

    public static List<ImageInfo> validateFeature(List<ZoneTrendImage> images, List<FeatureTrendInputs.Sheet> sheets) {
        Assert.isTrue(images != null && images.size() == sheets.size(), "走势图数量与特征不符");
        var result = new ArrayList<ImageInfo>();
        for (int index = 0; index < sheets.size(); index++) {
            var sheet = sheets.get(index);
            var image = images.get(index);
            Assert.isTrue(image != null && sheet.code().equals(image.code()), "走势图缺失、重复或顺序错误");
            result.add(validate(image, FeatureTrendInputs.IMAGE_WIDTH, sheet.height()));
        }
        return List.copyOf(result);
    }

    private static ImageInfo validate(ZoneTrendImage image, int expectedWidth, int expectedHeight) {
        String url = image.dataUrl();
        Assert.isTrue(url != null && url.startsWith(PREFIX) && url.length() <= 2_000_000, "图片必须是小于1.5MB的PNG数据");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(url.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片Base64编码无效", e);
        }
        Assert.isTrue(bytes.length >= 24 && ByteBuffer.wrap(bytes).getLong() == 0x89504e470d0a1a0aL, "图片不是有效的PNG");
        int width = ByteBuffer.wrap(bytes, 16, 8).getInt();
        int height = ByteBuffer.wrap(bytes, 20, 4).getInt();
        Assert.isTrue(width == expectedWidth && height == expectedHeight, "图片尺寸不正确，请重新生成走势图");
        // Check the header before decoding so an uploaded image cannot request an unbounded allocation.
        try (var input = new ByteArrayInputStream(bytes)) {
            var decoded = ImageIO.read(input);
            Assert.isTrue(decoded != null && decoded.getWidth() == width && decoded.getHeight() == height, "PNG图片无法解码");
        } catch (IOException e) {
            throw new IllegalArgumentException("PNG图片无法解码", e);
        }
        return new ImageInfo(image.code(), DigestUtil.sha256Hex(bytes), width, height);
    }
}

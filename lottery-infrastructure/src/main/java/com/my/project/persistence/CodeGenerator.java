package com.my.project.persistence;

// 演示例子，执行 main 方法控制台输入模块表名回车自动生成对应项目目录中
public class CodeGenerator {
//    public static void main(String[] args) {
//        FastAutoGenerator
//            .create("jdbc:mariadb://10.22.36.58:3306/lottery?useSSL=false&serverTimezone=UTC", "root", "123456")
//            .globalConfig(builder -> {
//                builder.author("liuqiang") // 设置作者
//                    .outputDir("E:\\studyProject\\lottery\\lottery-dao\\src\\main\\java\\com\\my\\project\\dao\\temp"); // 指定输出目录
//            }).dataSourceConfig(builder -> builder.typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
//                int typeCode = metaInfo.getJdbcType().TYPE_CODE;
//                if (typeCode == Types.SMALLINT) {
//                    // 自定义类型转换
//                    return DbColumnType.INTEGER;
//                }
//                return typeRegistry.getColumnType(metaInfo);
//            })).packageConfig(builder -> builder.parent("com.my.project.dao") // 设置父包名
//                .moduleName("") // 设置父包模块名
//                .pathInfo(Collections.singletonMap(OutputFile.xml, "D://")) // 设置mapperXml生成路径
//            ).strategyConfig(builder -> builder.addInclude("t_lottery_recommendation_result") // 设置需要生成的表名
//                .addTablePrefix("t_", "c_") // 设置过滤表前缀
//            ).templateEngine(new FreemarkerTemplateEngine()) // 使用Freemarker引擎模板，默认的是Velocity引擎模板
//            .execute();
//    }
}
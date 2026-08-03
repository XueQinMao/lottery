package com.my.project.persistence.config;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * SetStringTypeHandler
 *
 * @author 刘强
 * @version 2025/10/29 17:41
 **/
@MappedTypes(Set.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class SetStringTypeHandler extends BaseTypeHandler<Set<Integer>> {
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Set<Integer> parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, StringUtils.join(parameter, ","));
    }

    @Override
    public Set<Integer> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        Set<Integer> set = new HashSet<>();
        Arrays.stream(value.split(",")).map(Integer::parseInt).forEach(set::add);

        return StringUtils.isBlank(value) ? new HashSet<>() : set;
    }

    @Override
    public Set<Integer> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        Set<Integer> set = new HashSet<>();
        Arrays.stream(value.split(",")).map(Integer::parseInt).forEach(set::add);

        return StringUtils.isBlank(value) ? new HashSet<>() : set;
    }

    @Override
    public Set<Integer> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        Set<Integer> set = new HashSet<>();
        Arrays.stream(value.split(",")).map(Integer::parseInt).forEach(set::add);

        return StringUtils.isBlank(value) ? new HashSet<>() : set;
    }
}

package com.simon.common.plugins.oauth;

import com.simon.common.config.AppConfig;
import com.simon.common.domain.UserEntity;
import com.simon.common.utils.DateUtil;
import com.simon.common.utils.ValidUtil;
import com.simon.model.Authority;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * @author simon
 * @date 2018-11-30
 **/

@Slf4j
@Component
@Primary
public class UsernamePasswordAuthenticator extends AbstractPreparableIntegrationAuthenticator {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    private MessageSource messageSource;

    private Locale locale = AppConfig.getLocale();

    private final String sqlLoadUserByPhone;
    private final String sqlLoadUserByEmail;
    private final String sqlLoadUserByName;
    private final String sqlLoadAuthorities;
    private final RowMapper<UserEntity> myUserDetailsRowMapper;
    private final RowMapper<Authority> authorityRowMapper;

    private final static String PASSWORD_AUTH_TYPE = "password";

    public UsernamePasswordAuthenticator() {
        sqlLoadUserByPhone = "select id,username,password,enabled,area_code,phone,email,address,birth,age,head_photo,person_brief,sex from t_users where phone=?";
        sqlLoadUserByEmail = "select id,username,password,enabled,area_code,phone,email,address,birth,age,head_photo,person_brief,sex from t_users where email=?";
        sqlLoadUserByName = "select id,username,password,enabled,area_code,phone,email,address,birth,age,head_photo,person_brief,sex from t_users where username=?";
        sqlLoadAuthorities = "select user_id,authority from t_authorities where user_id = ?";

        myUserDetailsRowMapper = (rs, i) -> new UserEntity(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString("area_code"), rs.getString("phone"), rs.getString("email"), rs.getString("address"), DateUtil.dateToLocalDate(rs.getDate("birth")), rs.getInt("age"), rs.getString("head_photo"), rs.getString("person_brief"), rs.getBoolean("sex"));

        authorityRowMapper = (rs, i) -> {
            Authority authority = new Authority();
            authority.setUserId(rs.getLong(1));
            authority.setAuthority(rs.getString(2));
            return authority;
        };
    }

    @Override
    public UserEntity authenticate(IntegrationAuthentication integrationAuthentication) {
        log.info("password authenticate");
        try {
            UserEntity userFromQuery = null;
            if (StringUtils.isEmpty(integrationAuthentication.getUsername())) {
                throw new UsernameNotFoundException(messageSource.getMessage("usernameNotFound", null, locale));
            }
            if (ValidUtil.isEmail(integrationAuthentication.getUsername())) {
                userFromQuery = jdbcTemplate.queryForObject(sqlLoadUserByEmail, myUserDetailsRowMapper, integrationAuthentication.getUsername());
            } else if (ValidUtil.isMobile(integrationAuthentication.getUsername())) {
                userFromQuery = jdbcTemplate.queryForObject(sqlLoadUserByPhone, myUserDetailsRowMapper, integrationAuthentication.getUsername());
            } else {
                userFromQuery = jdbcTemplate.queryForObject(sqlLoadUserByName, myUserDetailsRowMapper, integrationAuthentication.getUsername());
            }

            log.info("?????????????????????{}", userFromQuery);
            List<Authority> authorities = jdbcTemplate.query(sqlLoadAuthorities, authorityRowMapper, userFromQuery.getId());
            log.info("??????????????????{}", authorities);

            return new UserEntity(userFromQuery.getId(), userFromQuery.getUsername(), userFromQuery.getPassword(), userFromQuery.isEnabled(), userFromQuery.getAreaCode(), userFromQuery.getPhone(), userFromQuery.getEmail(), userFromQuery.getAddress(), userFromQuery.getBirth(), userFromQuery.getAge(), userFromQuery.getHeadPhoto(), userFromQuery.getPersonBrief(), userFromQuery.getSex(), authorities);
        } catch (EmptyResultDataAccessException e) {
            log.info("????????????????????????{}", integrationAuthentication.getUsername());
            throw new UsernameNotFoundException(messageSource.getMessage("usernameNotFound", null, locale));
        }
    }

    @Override
    public void prepare(IntegrationAuthentication integrationAuthentication) {
        log.info("password prepare");
    }

    @Override
    public boolean support(IntegrationAuthentication integrationAuthentication) {
        return StringUtils.isEmpty(integrationAuthentication.getAuthType());
    }
}

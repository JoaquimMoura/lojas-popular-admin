package br.com.lojaspopular.domain.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByEmail(String email);

	/** Usuários ativos com algum dos perfis informados. */
	@Query("select count(distinct u) from User u join u.roles r where u.enabled = true and r in :roles")
	long contarAtivosComPerfis(@Param("roles") Collection<Role> roles);

	@Query("select distinct u from User u join u.roles r where u.enabled = true and r in :roles")
	List<User> listarAtivosComPerfis(@Param("roles") Collection<Role> roles);
}

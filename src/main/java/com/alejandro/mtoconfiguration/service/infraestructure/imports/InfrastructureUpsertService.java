package com.alejandro.mtoconfiguration.service.infraestructure.imports;

import com.alejandro.mtoconfiguration.model.commons.BaseDTO;
import com.alejandro.mtoconfiguration.model.commons.LovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.CantileverDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ExecutionPackageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.ProfileDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.StationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.SteadyArmDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.TrackDTO;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.CantileverMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ExecutionPackageMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileLovCodes;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.ProfileMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.StationMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.infrastructure.imports.TrackMasterRow;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.AnchorageFoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.CantileverTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.DisconnectorFunctionDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.FoundationDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PoleTypeDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.PortalDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ProfileStatusDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.ReturnSupportDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SectioningDTO;
import com.alejandro.mtoconfiguration.model.commons.SLovDTO;
import com.alejandro.mtoconfiguration.model.synchronous.lov.SteadyArmTypeDTO;
import com.alejandro.mtoconfiguration.core.exception.NotFoundException;
import com.alejandro.mtoconfiguration.core.exception.ValidationException;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.BusinessEntityRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ExecutionPackageRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.ProfileRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.StationRepository;
import com.alejandro.mtoconfiguration.repository.jpa.infrastructure.TrackRepository;
import com.alejandro.mtoconfiguration.service.commons.MasterDataService;
import com.alejandro.mtoconfiguration.service.infraestructure.ExecutionPackageService;
import com.alejandro.mtoconfiguration.service.infraestructure.ProfileService;
import com.alejandro.mtoconfiguration.service.infraestructure.StationService;
import com.alejandro.mtoconfiguration.service.infraestructure.TrackService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Alta o modificacion de una entidad de infraestructura por su clave natural.
 *
 * <h2>Por que uno a uno y no por el arbol entero</h2>
 *
 * <p>Seria una linea mandar un {@code PUT /execution-packages/{id}} con paquete, vias,
 * perfiles y mensulas. Y seria una bomba: en la reconciliacion de colecciones que
 * documenta {@code README_API.md} §4, <b>el hijo que no mandas se borra</b>. Un
 * reimport de un maestro al que le faltase una via borraria esa via y, en cascada, sus
 * perfiles y sus mensulas. El importador va entidad a entidad.
 *
 * <p>Cada {@code create}/{@code update} abre <b>su propia transaccion</b> —son metodos
 * {@code @Transactional} de otro bean, asi que el proxy las separa de verdad—, con el
 * mismo razonamiento que documenta {@code ProfileBulkJobRunner}: una fila mala cuesta
 * una fila y no las 11.714 anteriores, y el progreso es visible mientras corre.
 *
 * <h2>Idempotencia</h2>
 *
 * <p>Se busca por la clave natural antes de escribir, apoyandose en los indices unicos
 * parciales de V12. Reimportar el mismo maestro no crea nada: eso es lo que hace que la
 * carga se pueda repetir sin miedo, y lo que comprueba {@code ProfileMasterImportIT}.
 */
@Service
@RequiredArgsConstructor
public class InfrastructureUpsertService {

    private final ExecutionPackageService executionPackageService;
    private final StationService stationService;
    private final TrackService trackService;
    private final ProfileService profileService;

    private final ExecutionPackageRepository executionPackageRepository;
    private final StationRepository stationRepository;
    private final TrackRepository trackRepository;
    private final ProfileRepository profileRepository;
    private final BusinessEntityRepository businessEntityRepository;
    private final MasterDataService masterDataService;

    public UpsertResult upsertExecutionPackage(ExecutionPackageMasterRow row, boolean dryRun) {
        Optional<Long> existing = executionPackageRepository.findByNameIgnoreCase(row.name())
                .map(entity -> entity.getId());

        ExecutionPackageDTO dto = new ExecutionPackageDTO();
        dto.setName(row.name());
        dto.setInitialPackage(row.initialPackage());
        dto.setLength(row.length());
        dto.setStartDate(row.startDate());
        dto.setEndDate(row.endDate());
        dto.setEnabled(row.enabled());
        dto.setCompanyId(resolveCompany(row.companyIdentificationNumber()));

        return write(existing, dto, executionPackageService::create, executionPackageService::update,
                dryRun);
    }

    public UpsertResult upsertStation(StationMasterRow row, Long executionPackageId, boolean dryRun) {
        Optional<Long> existing = stationRepository
                .findByExecutionPackageIdAndNameIgnoreCase(executionPackageId, row.name())
                .map(entity -> entity.getId());

        StationDTO dto = new StationDTO();
        dto.setName(row.name());
        dto.setExecutionPackageId(executionPackageId);

        return write(existing, dto, stationService::create, stationService::update, dryRun);
    }

    public UpsertResult upsertTrack(TrackMasterRow row, Long executionPackageId, Long stationId,
                                    boolean dryRun) {
        Optional<Long> existing = trackRepository
                .findByExecutionPackageIdAndNameIgnoreCase(executionPackageId, row.name())
                .map(entity -> entity.getId());

        TrackDTO dto = new TrackDTO();
        dto.setName(row.name());
        dto.setEnabled(row.enabled());
        dto.setExecutionPackageId(executionPackageId);
        // Nulo es una respuesta valida: la via cuelga del paquete, no de una estacion.
        dto.setStationId(stationId);

        return write(existing, dto, trackService::create, trackService::update, dryRun);
    }

    /**
     * Alta o modificacion de un perfil <b>con sus mensulas</b>, en una sola transaccion.
     *
     * <p>Las mensulas viajan anidadas y no sueltas porque no tienen clave natural
     * ({@code Cantilever.equals} es solo por id): creadas aparte no habria forma de
     * saber cual es cual al reimportar. Se reconcilian por {@code SLOT} contra las que
     * ya existen, ordenadas por id, que es el orden que fija {@code @OrderBy} en la
     * entidad. Sin eso, cada reimport las borraria y las volveria a insertar, perdiendo
     * sus identificadores y su histórico de auditoria.
     */
    public UpsertResult upsertProfile(ProfileMasterRow row, Long trackId,
                                      List<CantileverMasterRow> cantilevers, boolean dryRun) {
        Optional<ProfileDTO> existing = profileRepository
                .findByTrackIdAndProfileIdIgnoreCase(trackId, row.profileId())
                .map(entity -> profileService.getById(entity.getId()));

        ProfileDTO dto = new ProfileDTO();
        dto.setProfileId(row.profileId());
        dto.setKp(row.kp());
        dto.setTrackId(trackId);
        dto.setSpan(row.span());
        dto.setHeightCantileverSupport(row.heightCantileverSupport());
        dto.setPoleGaugeLocation(row.poleGaugeLocation());
        dto.setRailPoleDistance(row.railPoleDistance());
        requireResolvableCodes(row, cantilevers);
        applyLovCodes(dto, row.profileStatus(), row.lov());
        dto.setCantilevers(buildCantilevers(cantilevers,
                existing.map(ProfileDTO::getCantilevers).orElse(List.of())));

        return write(existing.map(ProfileDTO::getId), dto, profileService::create,
                profileService::update, dryRun);
    }

    /**
     * Empareja cada mensula del maestro con la que ya existe en la misma posicion.
     *
     * <p>Las existentes llegan en el orden estable del {@code @OrderBy("id ASC")} de la
     * entidad, asi que el {@code SLOT} 1 del maestro es la primera, el 2 la segunda y
     * asi. Las que sobran no se mandan, y la reconciliacion del mapper las borra: es lo
     * correcto cuando un perfil pasa de tres mensulas a dos.
     */
    private List<CantileverDTO> buildCantilevers(List<CantileverMasterRow> rows,
                                                 List<CantileverDTO> existing) {
        List<CantileverDTO> sorted = existing.stream()
                .sorted(Comparator.comparing(CantileverDTO::getId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<CantileverDTO> result = new ArrayList<>();
        List<CantileverMasterRow> ordered = rows.stream()
                .sorted(Comparator.comparingInt(CantileverMasterRow::slot))
                .toList();

        for (int index = 0; index < ordered.size(); index++) {
            CantileverMasterRow row = ordered.get(index);
            CantileverDTO previous = index < sorted.size() ? sorted.get(index) : null;

            CantileverDTO dto = new CantileverDTO();
            dto.setId(previous == null ? null : previous.getId());
            dto.setStagger(row.stagger());
            dto.setCatenaryHeight(row.catenaryHeight());
            dto.setCwElevation(row.cwElevation());
            dto.setCwHeight(row.cwHeight());
            dto.setWindDeflection(row.windDeflection());
            dto.setArmAngle(row.armAngle());
            dto.setCantileverType(lov(row.cantileverType(), CantileverTypeDTO::new));
            dto.setSteadyArm(buildSteadyArm(row, previous));
            result.add(dto);
        }
        return result;
    }

    /**
     * El brazo solo se manda si el origen trae su tipo.
     *
     * <p>4.816 de las 14.592 mensulas del maestro no traen ninguno, y la relacion es
     * opcional desde V12. Mandar un brazo vacio crearia una fila sin sentido.
     */
    private SteadyArmDTO buildSteadyArm(CantileverMasterRow row, CantileverDTO previous) {
        if (StringUtils.isBlank(row.steadyArmType())) {
            return null;
        }

        SteadyArmDTO dto = new SteadyArmDTO();
        if (previous != null && previous.getSteadyArm() != null) {
            dto.setId(previous.getSteadyArm().getId());
        }
        dto.setLength(row.steadyArmLength());
        dto.setSteadyArmType(lov(row.steadyArmType(), SteadyArmTypeDTO::new));
        return dto;
    }

    private void applyLovCodes(ProfileDTO dto, String profileStatus, ProfileLovCodes lov) {
        dto.setProfileStatus(lov(profileStatus, ProfileStatusDTO::new));
        // El maestro trae los codigos separados por espacio: 'A/S P50' son DOS.
        dto.setSectionings(lovList(lov.sectioning(), SectioningDTO::new));
        dto.setAnchorages(lovList(lov.anchorage(), AnchorageDTO::new));
        dto.setAnchorageFoundation(lov(lov.anchorageFoundation(), AnchorageFoundationDTO::new));
        dto.setFoundation(lov(lov.foundation(), FoundationDTO::new));
        dto.setPoleType(lov(lov.poleType(), PoleTypeDTO::new));
        dto.setPortal(lov(lov.portal(), PortalDTO::new));
        dto.setReturnSupport(lov(lov.returnSupport(), ReturnSupportDTO::new));
        dto.setSectioningFeedings(lovList(lov.sectioningFeeding(), DisconnectorFunctionDTO::new));
    }

    /**
     * Una LOV se referencia por codigo; un codigo en blanco significa "el origen no lo
     * trae" y deja la relacion sin tocar.
     */
    private <T extends LovDTO> T lov(String code, Supplier<T> factory) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        T dto = factory.get();
        dto.setCode(code.trim());
        return dto;
    }

    /**
     * El paquete se declara con el NIF de la empresa y no con su id: un numero en un
     * YAML versionado no significa nada para quien lo revisa, y ademas cambia de entorno
     * a entorno. Si no existe se deja a null y lo rechaza el validador, que dira que
     * falta {@code companyId}.
     */
    /**
     * Traduce el NIF que declara el maestro al identificador de la empresa.
     *
     * <p><b>Busca, no da de alta.</b> Las empresas vienen de un maestro externo: este
     * repositorio no tiene migracion que siembre {@code business_entity} ni servicio ni
     * endpoint que la escriba, asi que un NIF que no este ahi no se puede resolver.
     *
     * <p>Por eso los dos casos fallan aqui, con su nombre y su motivo, en vez de devolver
     * {@code null} y dejar que el validador diga <i>«companyId es obligatorio»</i>: ese
     * mensaje nombra un campo que quien edita {@code topology.yml} no ve —alli se escribe
     * un NIF, no un id— y no distingue el hueco del NIF equivocado. Con once paquetes
     * apuntando a la misma empresa, un NIF mal tecleado tumbaba los once y el informe
     * repetia once veces que faltaba un campo que si estaba puesto.
     */
    /**
     * Comprueba que TODO codigo de lista de valores que trae la fila existe en su catalogo.
     *
     * <p>Sin esto la carga es peor que un fallo: {@code MasterDataService} resuelve un codigo
     * desconocido a {@code null} en silencio, el validador no consulta ningun catalogo —solo
     * exige que {@code profileStatus} venga informado— y el perfil se guarda con la clave
     * ajena vacia mientras el informe lo cuenta como cargado. Es exactamente lo que dejo
     * {@code profile_status} sin sembrar hasta V12, multiplicado por las diez relaciones.
     *
     * <p>Se juntan TODOS los codigos que fallan en un unico error en vez de parar en el
     * primero: quien corrige el maestro quiere la lista entera de una pasada, no descubrir
     * uno por ejecucion.
     */
    private void requireResolvableCodes(ProfileMasterRow row, List<CantileverMasterRow> cantilevers) {
        List<String> unresolved = new ArrayList<>();

        check(unresolved, "profileStatus", row.profileStatus(), masterDataService::getProfileStatusByCode);
        ProfileLovCodes lov = row.lov();
        // El seccionamiento viene en plural: se comprueba codigo a codigo.
        if (!StringUtils.isBlank(lov.sectioning())) {
            for (String code : lov.sectioning().trim().split("\\s+")) {
                check(unresolved, "sectioning", code, masterDataService::getSectioningByCode);
            }
        }
        if (!StringUtils.isBlank(lov.anchorage())) {
            for (String code : lov.anchorage().trim().split("\\s+")) {
                check(unresolved, "anchorage", code, masterDataService::getAnchorageByCode);
            }
        }
        check(unresolved, "anchorageFoundation", lov.anchorageFoundation(),
                masterDataService::getAnchorageFoundationByCode);
        check(unresolved, "foundation", lov.foundation(), masterDataService::getFoundationByCode);
        check(unresolved, "poleType", lov.poleType(), masterDataService::getPoleTypeByCode);
        check(unresolved, "portal", lov.portal(), masterDataService::getPortalByCode);
        check(unresolved, "returnSupport", lov.returnSupport(), masterDataService::getReturnSupportByCode);
        if (!StringUtils.isBlank(lov.sectioningFeeding())) {
            for (String code : lov.sectioningFeeding().trim().split("\s+")) {
                check(unresolved, "sectioningFeeding", code,
                        masterDataService::getDisconnectorFunctionByCode);
            }
        }

        for (CantileverMasterRow cantilever : cantilevers) {
            String slot = "cantilever[" + cantilever.slot() + "].";
            check(unresolved, slot + "cantileverType", cantilever.cantileverType(),
                    masterDataService::getCantileverTypeByCode);
            check(unresolved, slot + "steadyArmType", cantilever.steadyArmType(),
                    masterDataService::getSteadyArmTypeByCode);
        }

        if (!unresolved.isEmpty()) {
            throw new NotFoundException(
                    "codigos que no existen habilitados en su catalogo: "
                            + String.join(", ", unresolved)
                            + "; regenera el maestro con build_profile_master.py, que ahora los "
                            + "saca en NO_RECONOCIDO antes de importar");
        }
    }

    /**
     * Varios codigos en una celda, separados por espacio.
     *
     * <p>Solo el seccionamiento admite mas de uno: es corriente que un perfil de estacion lleve
     * 'A/S' y 'P50' a la vez. En las demas listas de valores una celda con dos codigos es una
     * anomalia, no el caso normal, y por eso alli sigue fallando.
     */
    private <T extends SLovDTO> List<T> lovList(String codes, Supplier<T> factory) {
        if (StringUtils.isBlank(codes)) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (String code : codes.trim().split("\\s+")) {
            T dto = factory.get();
            dto.setCode(code);
            result.add(dto);
        }
        return result;
    }

    /** Un codigo en blanco es un hueco legitimo; uno informado tiene que resolver. */
    private <T> void check(List<String> unresolved, String field, String code,
                           Function<String, T> resolver) {
        if (StringUtils.isBlank(code)) {
            return;
        }
        if (resolver.apply(code.trim()) == null) {
            unresolved.add(field + "='" + code.trim() + "'");
        }
    }

    private Long resolveCompany(String identificationNumber) {
        if (StringUtils.isBlank(identificationNumber)) {
            throw new ValidationException(
                    "el paquete no declara company_identification_number: rellenalo en "
                            + "data/tools/topology.yml y vuelve a generar el maestro");
        }
        String nif = identificationNumber.trim();
        return businessEntityRepository.findByIdentificationNumber(nif)
                .map(entity -> entity.getId())
                .orElseThrow(() -> new NotFoundException(
                        "no existe ninguna empresa con NIF '" + nif + "' en business_entity; "
                                + "las empresas vienen de un maestro externo, asi que revisa el "
                                + "NIF en data/tools/topology.yml o da de alta la empresa antes "
                                + "de importar"));
    }

    private <T extends BaseDTO> UpsertResult write(
            Optional<Long> existingId, T dto,
            Function<T, T> create, Function<T, T> update, boolean dryRun) {

        if (existingId.isEmpty()) {
            if (dryRun) {
                return UpsertResult.created(null);
            }
            return UpsertResult.created(create.apply(dto).getId());
        }

        Long id = existingId.get();
        dto.setId(id);
        if (dryRun) {
            return UpsertResult.updated(id);
        }
        return UpsertResult.updated(Objects.requireNonNullElse(update.apply(dto).getId(), id));
    }

    /**
     * Que se hizo con una fila y con que identificador se quedo.
     *
     * <p>En simulacion el identificador de un alta es null: no se ha escrito nada, asi
     * que no hay id que dar. Los hijos de esa fila se cuentan igual pero no se escriben.
     */
    public record UpsertResult(Long id, boolean created) {
        static UpsertResult created(Long id) {
            return new UpsertResult(id, true);
        }

        static UpsertResult updated(Long id) {
            return new UpsertResult(id, false);
        }
    }
}

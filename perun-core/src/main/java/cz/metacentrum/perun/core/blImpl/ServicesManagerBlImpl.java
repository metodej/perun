package cz.metacentrum.perun.core.blImpl;

import cz.metacentrum.perun.audit.events.ServicesManagerEvents.AllRequiredAttributesRemovedFromService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.AttributeAddedAsRequiredToService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.AttributesAddedAsRequiredToService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.DestinationAddedToServiceAndFacility;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.DestinationCreated;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.DestinationRemovedFromService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.DestinationsRemovedFromAllServices;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.DestinationsRemovedFromService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.RequiredAttributeRemovedFromService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.RequiredAttributesRemovedFromService;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServiceAddedToServicePackage;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServiceCreated;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServiceDeleted;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServiceRemovedFromServicesPackage;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServiceUpdated;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServicesPackageCreated;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServicesPackageDeleted;
import cz.metacentrum.perun.audit.events.ServicesManagerEvents.ServicesPackageUpdated;
import cz.metacentrum.perun.core.api.AttributesManager;
import cz.metacentrum.perun.core.api.exceptions.AttributeNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.WrongAttributeAssignmentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.metacentrum.perun.core.api.Attribute;
import cz.metacentrum.perun.core.api.AttributeDefinition;
import cz.metacentrum.perun.core.api.Destination;
import cz.metacentrum.perun.core.api.Facility;
import cz.metacentrum.perun.core.api.Group;
import cz.metacentrum.perun.core.api.Host;
import cz.metacentrum.perun.core.api.Member;
import cz.metacentrum.perun.core.api.PerunSession;
import cz.metacentrum.perun.core.api.Resource;
import cz.metacentrum.perun.core.api.RichDestination;
import cz.metacentrum.perun.core.api.Service;
import cz.metacentrum.perun.core.api.ServiceAttributes;
import cz.metacentrum.perun.core.api.ServicesPackage;
import cz.metacentrum.perun.core.api.User;
import cz.metacentrum.perun.core.api.Vo;
import cz.metacentrum.perun.core.api.VosManager;
import cz.metacentrum.perun.core.api.exceptions.AttributeAlreadyAssignedException;
import cz.metacentrum.perun.core.api.exceptions.AttributeNotAssignedException;
import cz.metacentrum.perun.core.api.exceptions.ConsistencyErrorException;
import cz.metacentrum.perun.core.api.exceptions.DestinationAlreadyAssignedException;
import cz.metacentrum.perun.core.api.exceptions.DestinationAlreadyRemovedException;
import cz.metacentrum.perun.core.api.exceptions.DestinationExistsException;
import cz.metacentrum.perun.core.api.exceptions.DestinationNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.GroupResourceMismatchException;
import cz.metacentrum.perun.core.api.exceptions.InternalErrorException;
import cz.metacentrum.perun.core.api.exceptions.MemberResourceMismatchException;
import cz.metacentrum.perun.core.api.exceptions.RelationExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServiceAlreadyAssignedException;
import cz.metacentrum.perun.core.api.exceptions.ServiceAlreadyRemovedException;
import cz.metacentrum.perun.core.api.exceptions.ServiceAlreadyRemovedFromServicePackageException;
import cz.metacentrum.perun.core.api.exceptions.ServiceExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServiceNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServicesPackageExistsException;
import cz.metacentrum.perun.core.api.exceptions.ServicesPackageNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.UserNotExistsException;
import cz.metacentrum.perun.core.api.exceptions.VoNotExistsException;
import cz.metacentrum.perun.core.bl.PerunBl;
import cz.metacentrum.perun.core.bl.ServicesManagerBl;
import cz.metacentrum.perun.core.impl.Utils;
import cz.metacentrum.perun.core.implApi.ServicesManagerImplApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Michal Prochazka <michalp@ics.muni.cz>
 * @author Slavek Licehammer <glory@ics.muni.cz>
 */
public class ServicesManagerBlImpl implements ServicesManagerBl {

	final static Logger log = LoggerFactory.getLogger(ServicesManagerBlImpl.class);

	private final ServicesManagerImplApi servicesManagerImpl;
	private PerunBl perunBl;

	public ServicesManagerBlImpl(ServicesManagerImplApi servicesManagerImpl) {
		this.servicesManagerImpl = servicesManagerImpl;
	}

	@Override
	public Service createService(PerunSession sess, Service service) throws InternalErrorException, ServiceExistsException {
		//check if service with same name exists in perun
		try {
			Service s = getServicesManagerImpl().getServiceByName(sess, service.getName());
			throw new ServiceExistsException(s);
		} catch(ServiceNotExistsException ex) { /* OK */ }

		getPerunBl().getAuditer().log(sess, new ServiceCreated(service));
		return getServicesManagerImpl().createService(sess, service);
	}

	@Override
	public void deleteService(PerunSession sess, Service service) throws InternalErrorException, RelationExistsException, ServiceAlreadyRemovedException {
		// Check if the relation with the resources exists
		if (this.getAssignedResources(sess, service).size() > 0) {
			throw new RelationExistsException("Service is defined on some resource");
		}

		getServicesManagerImpl().removeAllRequiredAttributes(sess, service);
		getServicesManagerImpl().deleteService(sess, service);
		getPerunBl().getAuditer().log(sess, new ServiceDeleted(service));
	}

	@Override
	public void updateService(PerunSession sess, Service service) throws InternalErrorException {
		Utils.notNull(service.getName(), "service.name");
		getServicesManagerImpl().updateService(sess, service);
		getPerunBl().getAuditer().log(sess, new ServiceUpdated(service));
	}

	@Override
	public Service getServiceById(PerunSession sess, int id) throws InternalErrorException, ServiceNotExistsException {
		return getServicesManagerImpl().getServiceById(sess, id);
	}

	@Override
	public Service getServiceByName(PerunSession sess, String name) throws InternalErrorException, ServiceNotExistsException {
		return getServicesManagerImpl().getServiceByName(sess, name);
	}

	@Override
	public List<Service> getServices(PerunSession sess) throws InternalErrorException {
		return getServicesManagerImpl().getServices(sess);
	}

	@Override
	public List<Service> getServicesByAttributeDefinition(PerunSession sess, AttributeDefinition attributeDefinition) throws InternalErrorException {
		return getServicesManagerImpl().getServicesByAttributeDefinition(sess, attributeDefinition);
	}

	@Override
	public List<Resource> getAssignedResources(PerunSession sess, Service service) throws InternalErrorException {
		return getServicesManagerImpl().getAssignedResources(sess, service);
	}

	private ServiceAttributes getData(PerunSession sess, Service service, Resource resource) throws InternalErrorException {
		ServiceAttributes resourceServiceAttributes = new ServiceAttributes();
		resourceServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resource));

		List<Member> members;
		members = getPerunBl().getResourcesManagerBl().getAllowedMembers(sess, resource);
		HashMap<Member, List<Attribute>> attributes;

		try {
			attributes = getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, null, resource, members, true);
		} catch(MemberResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}

		for (Member mem : attributes.keySet()) {
			ServiceAttributes serviceAttributes = new ServiceAttributes();
			serviceAttributes.addAttributes(attributes.get(mem));
			resourceServiceAttributes.addChildElement(serviceAttributes);
		}

		return resourceServiceAttributes;
	}

	private ServiceAttributes getData(PerunSession sess, Service service, Facility facility, Resource resource, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes resourceServiceAttributes = new ServiceAttributes();
		resourceServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resource));

		List<Member> members;
		members = getPerunBl().getResourcesManagerBl().getAllowedMembers(sess, resource);

		if (filterExpiredMembers) {
			List<Member> membersToRemove = new ArrayList<>();
			for (Member member : members) {
				try {
					Attribute groupStatus = getPerunBl().getAttributesManagerBl().getAttribute(sess, member, resource, AttributesManager.NS_MEMBER_RESOURCE_ATTR_VIRT + ":groupStatus");
					if (groupStatus == null) {
						throw new InternalErrorException("groupStatus is null");
					}
					if (groupStatus.getValue() == "EXPIRED") {
						membersToRemove.add(member);
					}
				} catch (MemberResourceMismatchException | WrongAttributeAssignmentException | AttributeNotExistsException e) {
					throw new InternalErrorException(e);
				}
			}
			members.removeAll(membersToRemove);
		}

		HashMap<Member, List<Attribute>> attributes;

		try {
			attributes = getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility, resource, members, true);
		} catch(MemberResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}

		for (Member mem : attributes.keySet()) {
			ServiceAttributes serviceAttributes = new ServiceAttributes();
			serviceAttributes.addAttributes(attributes.get(mem));
			resourceServiceAttributes.addChildElement(serviceAttributes);
		}

		return resourceServiceAttributes;

	}

	private ServiceAttributes getDataWithGroups(PerunSession sess, Service service, Facility facility, Resource resource, boolean filterExpiredMembers) throws InternalErrorException {

		// append resource attributes
		ServiceAttributes resourceServiceAttributes = new ServiceAttributes();
		resourceServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resource));

		// append also vo attributes to resource object
		try {
			Vo resourceVo = getPerunBl().getVosManagerBl().getVoById(sess, resource.getVoId());
			resourceServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resourceVo));
		} catch (VoNotExistsException ex) {
			throw new ConsistencyErrorException("There is missing Vo for existing resource " + resource);
		}

		ServiceAttributes membersAbstractSA = new ServiceAttributes();
		Map<Member, ServiceAttributes> memberAttributes = new HashMap<>();
		List<Member> members = getPerunBl().getResourcesManagerBl().getAllowedMembers(sess, resource);

		if (filterExpiredMembers) {
			List<Member> membersToRemove = new ArrayList<>();
			for (Member member : members) {
				try {
					Attribute groupStatus = getPerunBl().getAttributesManagerBl().getAttribute(sess, member, resource, AttributesManager.NS_MEMBER_RESOURCE_ATTR_VIRT + ":groupStatus");
					if (groupStatus.getValue() == "EXPIRED") {
						membersToRemove.add(member);
					}
				} catch (MemberResourceMismatchException | WrongAttributeAssignmentException | AttributeNotExistsException e) {
					throw new InternalErrorException(e);
				}
			}
			members.removeAll(membersToRemove);
		}

		HashMap<Member, List<Attribute>> attributes;

		try {
			// append all member/member_resource/user/user_facility attributes
			attributes = getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility, resource, members, true);
		} catch(MemberResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}

		for (Member mem : attributes.keySet()) {
			ServiceAttributes tmpAttributes = new ServiceAttributes();
			tmpAttributes.addAttributes(attributes.get(mem));
			memberAttributes.put(mem, tmpAttributes);
			membersAbstractSA.addChildElement(tmpAttributes);
		}

		// get all groups and append their sub structure
		ServiceAttributes groupsAbstractSA = new ServiceAttributes();
		List<Group> groups = getPerunBl().getResourcesManagerBl().getAssignedGroups(sess, resource);
		for(Group group: groups) {
			groupsAbstractSA.addChildElement(getData(sess, service, facility, resource, group, memberAttributes, filterExpiredMembers));
		}

		//assign abstract services attributes to resource service attributes
		resourceServiceAttributes.addChildElement(groupsAbstractSA);
		resourceServiceAttributes.addChildElement(membersAbstractSA);

		return resourceServiceAttributes;
	}

	private ServiceAttributes getDataWithVo(PerunSession sess, Service service, Facility facility, Vo vo, List<Resource> resources, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes voServiceAttributes = new ServiceAttributes();
		voServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, vo));

		for(Resource resource: resources) {
			ServiceAttributes resourceServiceAttributes = getDataWithGroups(sess, service, facility, resource, filterExpiredMembers);
			voServiceAttributes.addChildElement(resourceServiceAttributes);
		}

		return voServiceAttributes;
	}

	private ServiceAttributes getData(PerunSession sess, Service service, Facility facility, Resource resource, Group group, Map<Member, ServiceAttributes> memberAttributes, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes groupServiceAttributes = new ServiceAttributes();
		try {
			// add group and group_resource attributes
			groupServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resource, group, true));
		} catch (GroupResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}
		ServiceAttributes groupsSubGroupsElement = new ServiceAttributes();
		// FIXME Do not get subgroups of the members group
		if (!group.getName().equals(VosManager.MEMBERS_GROUP)) {
			List<Group> subGroups = getPerunBl().getGroupsManagerBl().getSubGroups(sess, group);
			for(Group subGroup : subGroups) {
				groupsSubGroupsElement.addChildElement(getData(sess, service, facility, resource, subGroup, memberAttributes, filterExpiredMembers));
			}
		}

		ServiceAttributes groupsMembersElement = new ServiceAttributes();
		//Invalid and disabled are not allowed here
		List<Member> members = getPerunBl().getGroupsManagerBl().getGroupMembersExceptInvalidAndDisabled(sess, group);

		if (filterExpiredMembers) {
			List<Member> membersToRemove = new ArrayList<>();
			for (Member member : members) {
				try {
					Attribute groupStatus = getPerunBl().getAttributesManagerBl().getAttribute(sess, member, group, AttributesManager.NS_MEMBER_GROUP_ATTR_VIRT + ":groupStatus");
					if (groupStatus.getValue() == "EXPIRED") {
						membersToRemove.add(member);
					}
				} catch (WrongAttributeAssignmentException | AttributeNotExistsException e) {
					throw new InternalErrorException(e);
				}
			}
			members.removeAll(membersToRemove);
		}

		for(Member member : members) {
			// append also member_group attributes for each member in a group
			// rest of member/user attributes was passed in a param if present
			ServiceAttributes tempAttrs = new ServiceAttributes();
			if (memberAttributes.get(member) != null) {
				tempAttrs.addAttributes(memberAttributes.get(member).getAttributes());
			}
			tempAttrs.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, member, group));
			groupsMembersElement.addChildElement(tempAttrs);
		}

		groupServiceAttributes.addChildElement(groupsSubGroupsElement);
		groupServiceAttributes.addChildElement(groupsMembersElement);
		return groupServiceAttributes;
	}

	private ServiceAttributes getData(PerunSession sess, Service service, Resource resource, Member member) throws InternalErrorException {
		ServiceAttributes memberServiceAttributes = new ServiceAttributes();
		try {
			memberServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, member, resource, true));
		} catch(MemberResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}
		return memberServiceAttributes;
	}

	private ServiceAttributes getData(PerunSession sess, Service service, Facility facility, Resource resource, Member member) throws InternalErrorException {
		ServiceAttributes memberServiceAttributes = new ServiceAttributes();

		User user;
		try {
			user = getPerunBl().getUsersManagerBl().getUserById(sess, member.getUserId());
			memberServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility, resource, user, member));
		} catch (UserNotExistsException e) {
			throw new ConsistencyErrorException("Member has assigned non-existing user.", e);
		} catch (MemberResourceMismatchException ex) {
			throw new InternalErrorException(ex);
		}

		return memberServiceAttributes;
	}

	@Override
	public ServiceAttributes getHierarchicalData(PerunSession sess, Service service, Facility facility, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes serviceAttributes = new ServiceAttributes();
		serviceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility));

		List<Resource> resources = getPerunBl().getFacilitiesManagerBl().getAssignedResources(sess, facility);
		resources.retainAll(getAssignedResources(sess, service));
		for(Resource resource: resources) {
			ServiceAttributes resourceServiceAttributes = getData(sess, service, facility, resource, filterExpiredMembers);
			serviceAttributes.addChildElement(resourceServiceAttributes);
		}
		return serviceAttributes;
	}

	@Override
	public ServiceAttributes getFlatData(PerunSession sess, Service service, Facility facility, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes serviceAttributes = new ServiceAttributes();
		serviceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility));

		ServiceAttributes allResourcesServiceAttributes = new ServiceAttributes();
		List<Resource> facilityResources = getPerunBl().getFacilitiesManagerBl().getAssignedResources(sess, facility);
		facilityResources.retainAll(getAssignedResources(sess, service));
		for(Resource resource : facilityResources) {
			ServiceAttributes resourceServiceAttributes = new ServiceAttributes();
			resourceServiceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, resource));
			allResourcesServiceAttributes.addChildElement(resourceServiceAttributes);
		}

		ServiceAttributes allUsersServiceAttributes = new ServiceAttributes();
		List<User> facilityUsers = getPerunBl().getFacilitiesManagerBl().getAllowedUsers(sess, facility, null, service);

		if (filterExpiredMembers) {
			List<User> usersToRemove = new ArrayList<>();
			for (User user : facilityUsers) {
				try {
					Attribute attribute = getPerunBl().getAttributesManagerBl().getAttribute(sess, facility, user, AttributesManager.NS_USER_FACILITY_ATTR_VIRT+ ":groupStatus");
					if (attribute.getValue() == "EXPIRED") {
						usersToRemove.add(user);
					}
				} catch (WrongAttributeAssignmentException | AttributeNotExistsException e) {
					throw new InternalErrorException(e);
				}
			}
			facilityUsers.removeAll(usersToRemove);
		}

		// get attributes for all users at once !
		HashMap<User, List<Attribute>> userFacilityAttributes = getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility, facilityUsers);
		HashMap<User, List<Attribute>> userAttributes = getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facilityUsers);

		for (User user : facilityUsers) {
			ServiceAttributes userServiceAttributes = new ServiceAttributes();
			// Depending on a service requirements we might get null user or user-facility attributes
			if (userAttributes.get(user) != null) userServiceAttributes.addAttributes(userAttributes.get(user));
			if (userFacilityAttributes.get(user) != null) userServiceAttributes.addAttributes(userFacilityAttributes.get(user));
			allUsersServiceAttributes.addChildElement(userServiceAttributes);
		}

		serviceAttributes.addChildElement(allResourcesServiceAttributes);
		serviceAttributes.addChildElement(allUsersServiceAttributes);

		return serviceAttributes;


	}

	@Override
	public ServiceAttributes getDataWithVos(PerunSession sess, Service service, Facility facility, boolean filterExpiredMembers) throws InternalErrorException, VoNotExistsException {
		ServiceAttributes serviceAttributes = new ServiceAttributes();
		serviceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility));

		//Get resources only for facility and service
		List<Resource> resources = getPerunBl().getFacilitiesManagerBl().getAssignedResources(sess, facility);
		resources.retainAll(getAssignedResources(sess, service));

		//Get all vos for these resources
		Set<Integer> vosIds = new HashSet<>();
		for(Resource resource: resources) {
			vosIds.add(resource.getVoId());
		}

		List<Vo> vos = new ArrayList<>();
		for(Integer voId: vosIds) {
			vos.add(getPerunBl().getVosManagerBl().getVoById(sess, voId));
		}

		for(Vo vo: vos) {
			List<Resource> voResources = getPerunBl().getResourcesManagerBl().getResources(sess, vo);
			voResources.retainAll(resources);
			ServiceAttributes voServiceAttributes = getDataWithVo(sess, service, facility, vo, voResources, filterExpiredMembers);
			serviceAttributes.addChildElement(voServiceAttributes);
		}

		return serviceAttributes;
	}

	@Override
	public ServiceAttributes getDataWithGroups(PerunSession sess, Service service, Facility facility, boolean filterExpiredMembers) throws InternalErrorException {
		ServiceAttributes serviceAttributes = new ServiceAttributes();
		serviceAttributes.addAttributes(getPerunBl().getAttributesManagerBl().getRequiredAttributes(sess, service, facility));

		List<Resource> resources = getPerunBl().getFacilitiesManagerBl().getAssignedResources(sess, facility);
		resources.retainAll(getAssignedResources(sess, service));
		for(Resource resource: resources) {
			ServiceAttributes resourceServiceAttributes = getDataWithGroups(sess, service, facility, resource, filterExpiredMembers);
			serviceAttributes.addChildElement(resourceServiceAttributes);
		}
		return serviceAttributes;
	}

	@Override
	public List<ServicesPackage> getServicesPackages(PerunSession sess) throws InternalErrorException {
		return getServicesManagerImpl().getServicesPackages(sess);
	}

	@Override
	public ServicesPackage getServicesPackageById(PerunSession sess, int servicesPackageId) throws InternalErrorException, ServicesPackageNotExistsException {
		return getServicesManagerImpl().getServicesPackageById(sess, servicesPackageId);
	}

	@Override
	public ServicesPackage getServicesPackageByName(PerunSession sess, String name) throws InternalErrorException, ServicesPackageNotExistsException {
		return getServicesManagerImpl().getServicesPackageByName(sess, name);
	}

	@Override
	public ServicesPackage createServicesPackage(PerunSession sess, ServicesPackage servicesPackage) throws InternalErrorException, ServicesPackageExistsException {
		Utils.notNull(servicesPackage.getDescription(), "servicesPackage.getDescription()");
		Utils.notNull(servicesPackage.getName(), "servicesPackage.getName()");

		//check if servicesPackage with same name exists in perun
		try {
			ServicesPackage s = getServicesManagerImpl().getServicesPackageByName(sess, servicesPackage.getName());
			throw new ServicesPackageExistsException(s);
		} catch(ServicesPackageNotExistsException ex) { /* OK */ }

		getPerunBl().getAuditer().log(sess, new ServicesPackageCreated(servicesPackage));
		return getServicesManagerImpl().createServicesPackage(sess, servicesPackage);
	}

	@Override
	public void updateServicesPackage(PerunSession sess, ServicesPackage servicesPackage) throws InternalErrorException {
		Utils.notNull(servicesPackage.getDescription(), "servicesPackage.getDescription()");
		Utils.notNull(servicesPackage.getName(), "servicesPackage.getName()");
		getServicesManagerImpl().updateServicesPackage(sess, servicesPackage);
		getPerunBl().getAuditer().log(sess, new ServicesPackageUpdated(servicesPackage));
	}

	@Override
	public void deleteServicesPackage(PerunSession sess, ServicesPackage servicesPackage) throws InternalErrorException, RelationExistsException {
		if(getServicesFromServicesPackage(sess, servicesPackage).isEmpty()) {
			getServicesManagerImpl().deleteServicesPackage(sess, servicesPackage);
			getPerunBl().getAuditer().log(sess, new ServicesPackageDeleted(servicesPackage));
		} else {
			throw new RelationExistsException("There is one or more services in the services package. ServicesPackage=\"" + servicesPackage + "\"");
		}
	}

	@Override
	public void addServiceToServicesPackage(PerunSession sess, ServicesPackage servicesPackage, Service service) throws InternalErrorException, ServiceAlreadyAssignedException {
		getServicesManagerImpl().addServiceToServicesPackage(sess, servicesPackage, service);
		getPerunBl().getAuditer().log(sess, new ServiceAddedToServicePackage(service, servicesPackage));
	}

	@Override
	public void removeServiceFromServicesPackage(PerunSession sess, ServicesPackage servicesPackage, Service service) throws InternalErrorException, ServiceAlreadyRemovedFromServicePackageException {
		getServicesManagerImpl().removeServiceFromServicesPackage(sess, servicesPackage, service);
		getPerunBl().getAuditer().log(sess, new ServiceRemovedFromServicesPackage(service, servicesPackage));
	}

	@Override
	public List<Service> getServicesFromServicesPackage(PerunSession sess, ServicesPackage servicesPackage) throws InternalErrorException {
		return getServicesManagerImpl().getServicesFromServicesPackage(sess, servicesPackage);
	}

	@Override
	public void addRequiredAttribute(PerunSession sess, Service service, AttributeDefinition attribute) throws InternalErrorException, AttributeAlreadyAssignedException {
		//check if attribute isn't already added
		List<AttributeDefinition> requiredAttributes = getPerunBl().getAttributesManagerBl().getRequiredAttributesDefinition(sess, service);
		if(requiredAttributes.contains(attribute)) throw new AttributeAlreadyAssignedException(attribute);

		getServicesManagerImpl().addRequiredAttribute(sess, service, attribute);
		getPerunBl().getAuditer().log(sess,new AttributeAddedAsRequiredToService(attribute, service));
	}

	@Override
	public void addRequiredAttributes(PerunSession sess, Service service, List<? extends AttributeDefinition> attributes) throws InternalErrorException, AttributeAlreadyAssignedException {
		getServicesManagerImpl().addRequiredAttributes(sess, service, attributes);
		getPerunBl().getAuditer().log(sess, new AttributesAddedAsRequiredToService(attributes, service));
	}

	@Override
	public void removeRequiredAttribute(PerunSession sess, Service service, AttributeDefinition attribute) throws InternalErrorException, AttributeNotAssignedException {
		getServicesManagerImpl().removeRequiredAttribute(sess, service, attribute);
		getPerunBl().getAuditer().log(sess, new RequiredAttributeRemovedFromService(attribute, service));
	}

	@Override
	public void removeRequiredAttributes(PerunSession sess, Service service, List<? extends AttributeDefinition> attributes) throws InternalErrorException, AttributeNotAssignedException {
		getServicesManagerImpl().removeRequiredAttributes(sess, service, attributes);
		getPerunBl().getAuditer().log(sess, new RequiredAttributesRemovedFromService(attributes, service));
	}

	@Override
	public void removeAllRequiredAttributes(PerunSession sess, Service service) throws InternalErrorException {
		getServicesManagerImpl().removeAllRequiredAttributes(sess, service);
		getPerunBl().getAuditer().log(sess, new AllRequiredAttributesRemovedFromService(service));
	}

	@Override
	public Destination addDestination(PerunSession sess, Service service, Facility facility, Destination destination) throws InternalErrorException, DestinationAlreadyAssignedException {
		if(!getServicesManagerImpl().destinationExists(sess, destination)) {
			try {
				//Try to get the destination without id
				Destination existingDestination = getServicesManagerImpl().getDestination(sess, destination.getDestination(), destination.getType());
				// pass new propagation type from API to object retrieved from DB,
				// since it always contains PARALLEL type
				existingDestination.setPropagationType(destination.getPropagationType());
				destination = existingDestination;
			} catch(DestinationNotExistsException ex) {
				try {
					destination = createDestination(sess, destination);
				} catch(DestinationExistsException e) {
					throw new ConsistencyErrorException(e);
				}
			}
		}
		if(getServicesManagerImpl().destinationExists(sess, service, facility, destination)) throw new DestinationAlreadyAssignedException(destination);

		getServicesManagerImpl().addDestination(sess, service, facility, destination);
		getPerunBl().getAuditer().log(sess, new DestinationAddedToServiceAndFacility(destination, service, facility));
		return destination;
	}

	@Override
	public Destination addDestination(PerunSession perunSession, List<Service> services, Facility facility, Destination destination) throws InternalErrorException, DestinationAlreadyAssignedException {
		if(!getServicesManagerImpl().destinationExists(perunSession, destination)) {
			try {
				//Try to get the destination without id
				Destination existingDestination = getServicesManagerImpl().getDestination(perunSession, destination.getDestination(), destination.getType());
				// pass new propagation type from API to object retrieved from DB,
				// since it always contains PARALLEL type
				existingDestination.setPropagationType(destination.getPropagationType());
				destination = existingDestination;
			} catch(DestinationNotExistsException ex) {
				try {
					destination = createDestination(perunSession, destination);
				} catch(DestinationExistsException e) {
					throw new ConsistencyErrorException(e);
				}
			}
		}

		for(Service s: services) {
			if(!getServicesManagerImpl().destinationExists(perunSession, s, facility, destination)) {
				getServicesManagerImpl().addDestination(perunSession, s, facility, destination);
				getPerunBl().getAuditer().log(perunSession, new DestinationAddedToServiceAndFacility(destination, s, facility));
			}
		}

		return destination;
	}

	private Destination addDestinationEvenIfAlreadyExists(PerunSession sess, Service service, Facility facility, Destination destination) throws InternalErrorException {
		if(!getServicesManagerImpl().destinationExists(sess, destination)) {
			try {
				//Try to get the destination without id
				destination = getServicesManagerImpl().getDestination(sess, destination.getDestination(), destination.getType());
			} catch(DestinationNotExistsException ex) {
				try {
					destination = createDestination(sess, destination);
				} catch(DestinationExistsException e) {
					//This is ok, destination already exists so take it from DB
					try {
						destination = getServicesManagerImpl().getDestination(sess, destination.getDestination(), destination.getType());
					} catch (DestinationNotExistsException exep) {
						throw new ConsistencyErrorException("Destination seems to exists and not exists in the same time. There is some other problem." + exep);
					}
				}
			}
		}
		//if destination is already assigned, do not add message to the log and only return it back
		if(getServicesManagerImpl().destinationExists(sess, service, facility, destination)) return destination;

		getServicesManagerImpl().addDestination(sess, service, facility, destination);
		getPerunBl().getAuditer().log(sess, new DestinationAddedToServiceAndFacility(destination, service, facility));
		return destination;
	}

	@Override
	public void removeDestination(PerunSession sess, Service service, Facility facility, Destination destination) throws InternalErrorException, DestinationAlreadyRemovedException {
		if(!getServicesManagerImpl().destinationExists(sess, destination)) {
			try {
				//Try to get the destination without id
				destination = getServicesManagerImpl().getDestination(sess, destination.getDestination(), destination.getType());
			} catch(DestinationNotExistsException ex) {
				throw new DestinationAlreadyRemovedException(destination);
			}
		}
		getServicesManagerImpl().removeDestination(sess, service, facility, destination);
		//TODO remove destination from destination taable if is not used anymore
		getPerunBl().getAuditer().log(sess, new DestinationRemovedFromService(destination, service, facility));
	}

	@Override
	public Destination getDestinationById(PerunSession sess, int id) throws InternalErrorException, DestinationNotExistsException {
		return getServicesManagerImpl().getDestinationById(sess, id);
	}

	@Override
	public List<Destination> getDestinations(PerunSession sess, Service service, Facility facility) throws InternalErrorException {
		List<Destination> destinations = getServicesManagerImpl().getDestinations(sess, service, facility);

		return destinations;
	}

	@Override
	public List<Destination> getDestinations(PerunSession perunSession) throws InternalErrorException {
		List<Destination> destinations = getServicesManagerImpl().getDestinations(perunSession);

		return destinations;
	}

	@Override
	public List<Destination> getDestinations(PerunSession perunSession, Facility facility) throws InternalErrorException {
		return getServicesManagerImpl().getDestinations(perunSession, facility);
	}

	@Override
	public List<RichDestination> getAllRichDestinations(PerunSession perunSession, Facility facility) throws InternalErrorException{
		return getServicesManagerImpl().getAllRichDestinations(perunSession, facility);
	}

	@Override
	public List<RichDestination> getAllRichDestinations(PerunSession perunSession, Service service) throws InternalErrorException{
		return getServicesManagerImpl().getAllRichDestinations(perunSession, service);
	}

	@Override
	public List<RichDestination> getRichDestinations(PerunSession perunSession, Facility facility, Service service) throws InternalErrorException{
		return getServicesManagerImpl().getRichDestinations(perunSession, facility, service);
	}

	@Override
	public void removeAllDestinations(PerunSession sess, Service service, Facility facility) throws InternalErrorException {
		getServicesManagerImpl().removeAllDestinations(sess, service, facility);
		//TODO remove destination from destination taable if is not used anymore
		getPerunBl().getAuditer().log(sess, new DestinationsRemovedFromService(service, facility));
	}

	@Override
	public void removeAllDestinations(PerunSession perunSession, Facility facility) throws InternalErrorException {
		getServicesManagerImpl().removeAllDestinations(perunSession, facility);
		getPerunBl().getAuditer().log(perunSession, new DestinationsRemovedFromAllServices(facility));
	}

	@Override
	public void checkServiceExists(PerunSession sess, Service service) throws InternalErrorException, ServiceNotExistsException {
		getServicesManagerImpl().checkServiceExists(sess, service);
	}

	@Override
	public void checkServicesPackageExists(PerunSession sess, ServicesPackage servicesPackage) throws InternalErrorException, ServicesPackageNotExistsException {
		getServicesManagerImpl().checkServicesPackageExists(sess, servicesPackage);
	}

	@Override
	public int getDestinationIdByName(PerunSession sess, String name, String type) throws InternalErrorException, DestinationNotExistsException {
		return servicesManagerImpl.getDestination(sess, name, type).getId();
	}

	@Override
	public List<Service> getAssignedServices(PerunSession sess, Facility facility) throws InternalErrorException {
		return servicesManagerImpl.getAssignedServices(sess, facility);
	}

	public Destination createDestination(PerunSession sess, Destination destination) throws InternalErrorException, DestinationExistsException {
		if(getServicesManagerImpl().destinationExists(sess, destination)) throw new DestinationExistsException(destination);
		destination = getServicesManagerImpl().createDestination(sess, destination);
		getPerunBl().getAuditer().log(sess, new DestinationCreated(destination));
		return destination;
	}


	/**
	 * Gets the servicesManagerImpl.
	 *
	 * @return The servicesManagerImpl.
	 */
	private ServicesManagerImplApi getServicesManagerImpl() {
		return this.servicesManagerImpl;
	}

	/**
	 * Gets the perunBl.
	 *
	 * @return The perunBl.
	 */
	public PerunBl getPerunBl() {
		return this.perunBl;
	}



	public void setPerunBl(PerunBl perunBl) {
		this.perunBl = perunBl;
	}

	@Override
	public List<Destination> addDestinationsForAllServicesOnFacility(PerunSession sess, Facility facility, Destination destination)
	throws InternalErrorException, DestinationAlreadyAssignedException {
	List<Service> services = this.getAssignedServices(sess, facility);
	List<Destination> destinations = new ArrayList<>();

	for (Service service: services) {
		destinations.add(this.addDestination(sess, service, facility, destination));
	}

	return destinations;
	}

	@Override
	public List<Destination> addDestinationsDefinedByHostsOnFacility(PerunSession perunSession, Service service, Facility facility) throws InternalErrorException, DestinationAlreadyAssignedException {
		// Get all hosts
		List<Host> hosts = getPerunBl().getFacilitiesManagerBl().getHosts(perunSession, facility);
		List<Destination> destinations = new ArrayList<>();

		for (Host host: hosts) {
			if (host.getHostname() != null && !host.getHostname().isEmpty()) {
				Destination destination = new Destination();
				destination.setDestination(host.getHostname());
				destination.setType(Destination.DESTINATIONHOSTTYPE);
				destinations.add(this.addDestination(perunSession, service, facility, destination));
			}
		}

		return destinations;
	}

	@Override
	public List<Destination> addDestinationsDefinedByHostsOnFacility(PerunSession perunSession, List<Service> services, Facility facility) throws InternalErrorException {
		List<Host> hosts = getPerunBl().getFacilitiesManagerBl().getHosts(perunSession, facility);
		List<Destination> destinations = new ArrayList<>();

		for (Service service: services) {
			for (Host host: hosts) {
				if (host.getHostname() != null && !host.getHostname().isEmpty()) {
					Destination destination = new Destination();
					destination.setDestination(host.getHostname());
					destination.setType(Destination.DESTINATIONHOSTTYPE);
					destinations.add(this.addDestinationEvenIfAlreadyExists(perunSession, service, facility, destination));
				}
			}
		}
		return destinations;
	}

	@Override
	public List<Destination> addDestinationsDefinedByHostsOnFacility(PerunSession perunSession, Facility facility) throws InternalErrorException {
		//First generate services
		List<Service> services = getPerunBl().getServicesManagerBl().getAssignedServices(perunSession, facility);
		return this.addDestinationsDefinedByHostsOnFacility(perunSession, services, facility);
	}


	@Override
	public List<Destination> getFacilitiesDestinations(PerunSession sess, Vo vo) throws InternalErrorException {
		List<Destination> destinations = getServicesManagerImpl().getFacilitiesDestinations(sess, vo);
		return destinations;
	}

	@Override
	public int getDestinationsCount(PerunSession sess) throws InternalErrorException {
		return getServicesManagerImpl().getDestinationsCount(sess);
	}
}

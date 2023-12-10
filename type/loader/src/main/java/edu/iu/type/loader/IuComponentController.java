package edu.iu.type.loader;

import java.lang.ModuleLayer.Controller;
import java.lang.module.ModuleDescriptor;

/**
 * Provides references for setting up {@link Module} access rules after creating
 * the a <strong>component's</strong> {@link ModuleLayer}, before any of its are
 * loaded.
 */
public interface IuComponentController {

	/**
	 * Gets a reference to the loaded {@code iu.util.type.impl} module.
	 * 
	 * @return {@link Module}
	 */
	Module getTypeModule();

	/**
	 * Gets a reference to the module described by the <strong>component
	 * archive's</strong> {@link ModuleDescriptor module descriptor}.
	 * 
	 * @return {@link Module}
	 */
	Module getComponentModule();

	/**
	 * Gets a reference to the {@link Controller} for the module layer created in
	 * conjunction with this loader.
	 * 
	 * <p>
	 * API Note from {@link Controller}: <em>Care should be taken with Controller
	 * objects, they should never be shared with untrusted code.</em>
	 * </p>
	 * 
	 * @return {@link Controller}
	 */
	Controller getController();

}

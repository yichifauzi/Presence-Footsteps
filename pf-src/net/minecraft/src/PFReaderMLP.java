package net.minecraft.src;

import eu.ha3.mc.presencefootsteps.mcpackage.interfaces.Isolator;

/* x-placeholder-wtfplv2 */

public class PFReaderMLP extends PFReader4P
{
	public PFReaderMLP(Isolator isolator)
	{
		super(isolator);
	}
	
	@Override
	public void generateFootsteps(EntityPlayer ply)
	{
		//if (true)
		//	throw new Minecraft161NotYetFixedRuntimeException();
		
		// recomment on fix
		//Pony pony = Pony.getPonyFromRegistry(ply, this.mod.manager().getMinecraft().renderEngine);
		//this.isPegasus = pony != null ? pony.isPegasus() : false;
		
		super.generateFootsteps(ply);
	}
}

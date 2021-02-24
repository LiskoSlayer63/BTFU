package btfubackup;

import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import org.apache.commons.lang3.tuple.Pair;

public class ExtensionPoints {
	public static void register() {
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(
				() -> FMLNetworkConstants.IGNORESERVERONLY,
				(a, b) -> true
		));
	}
}

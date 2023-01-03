package de.mpicbg.ulman.fusion;

import picocli.CommandLine;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@CommandLine.Command(name = "ParamsTest", version = "0.123", description = "some global info", mixinStandardHelpOptions = true)
@Plugin(type = Command.class, menuPath = "Plugins>ParamsTest")
public class ParamsTest implements Command {

	@CommandLine.Option(names = {"-p1"}, description = "param1")
	@Parameter(label = "param1")
	int param = 0;

	@Override
	public void run() {
		System.out.println("Hello from ParamsTest, param = "+param);
	}

	// ================================ CLI ==========================
	@CommandLine.Option(arity = "0", names = {"-fiji"}, description = "should start Fiji")
	boolean startFiji = false;

	public static void main(String[] args) {
		final ParamsTest app = CommandLine.populateCommand(new ParamsTest(), args);
		if (!app.startFiji)
			app.run();
		else
			System.out.println("bailing out 'cause Fiji GUI was requested...");
	}
}
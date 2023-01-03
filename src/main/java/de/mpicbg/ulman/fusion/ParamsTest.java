package de.mpicbg.ulman.fusion;

import picocli.CommandLine;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@CommandLine.Command(name = "ParamsTest")
@Plugin(type = Command.class, menuPath = "Plugins>ParamsTest")
public class ParamsTest implements Command {
	//NB: the long param name is the same as Fiji is using
	@CommandLine.Option(names = {"-p","param1"}, description = "parameter 1")
	@Parameter(label = "param1")
	int param = 0;

	// ================================ Workhorse ==========================
	@Override
	public void run() {
		System.out.println("Hello from ParamsTest, param = "+param);
	}

	// ================================ CLI ==========================
	@CommandLine.Option(names = {"-h", "--help"}, usageHelp = true, description = "display this help message")
	boolean usageHelpRequested;

	public void printHelp() {
		System.out.println("my own help text");
		System.out.println(); //intentional line separator
		CommandLine.usage(this, System.out);
	}

	public static void main(String[] args) {
		ParamsTest app;

		//parse the command line and fill the object's attributes
		try { app = CommandLine.populateCommand(new ParamsTest(), args); }
		catch (CommandLine.ParameterException pe) {
			System.out.println(pe.getMessage());
			System.out.println(); //intentional line separator
			new ParamsTest().printHelp();
			return;
		}

		//parsing went well:
		//no params given or an explicit cry for help?
		if (args.length == 0 || app.usageHelpRequested) {
			app.printHelp();
			return;
		}

		app.run();
	}
}
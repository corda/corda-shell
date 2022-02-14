# corda-shell

The Corda Shell is an application that allows the user to interact with a running Corda node.

The shell can be used in two ways:

- A standalone application - Run the `corda-shell` jar using:

    ```shell
     java -jar corda-shell-4.9.jar [-hvV] [--logging-level=<loggingLevel>] [--password=<password>]
        [--truststore-file=<trustStoreFile>]
        [--truststore-password=<trustStorePassword>]
        [--truststore-type=<trustStoreType>] [--user=<user>] [-a=<host>]
        [-c=<cordappDirectory>] [-f=<configFile>] [-o=<commandsDirectory>]
        [-p=<port>]
    ```

  Where:

    - `config-file=<configFile>`, `--f`: The path to the shell configuration file, used instead of providing the rest of the command line options.
    - `cordapp-directory=<cordappDirectory>`, `-c`: The path to the directory containing CorDapp jars, CorDapps are required when starting flows.
    - `commands-directory=<commandsDirectory>`, `-o`: The path to the directory containing additional CRaSH shell commands.
    - `host`, `-a`: The host address of the Corda node.
    - `port`, `-p`: The RPC port of the Corda node.
    - `user=<user>`: The RPC user name.
    - `password=<password>`: The RPC user password. If not provided it will be prompted for on startup.
    - `truststore-password=<trustStorePassword>`: The password to unlock the TrustStore file.
    - `truststore-file=<trustStoreFile>`: The path to the TrustStore file.
    - `truststore-type=<trustStoreType>`: The type of the TrustStore (for example, JKS).
    - `verbose`, `--log-to-console`, `-v`: If set, prints logging to the console as well as to a file.
    - `logging-level=<loggingLevel>`: Enable logging at this level and higher. Possible values: ERROR, WARN, INFO, DEBUG, TRACE. Default: INFO.
    - `help`, `-h`: Show this help message and exit.
    - `version`, `-V`: Print version information and exit.
    

- A driver within a Corda node. Install the `corda-shell` jar in a node's `/drivers` directory to run the shell in the same terminal that starts the node. By default, a Corda node does not run the shell.

    When using `cordaformation` the shell can be included in generated node's by including the following in the `build.gradle` file containing `deployNodes`:
  
    ```groovy
    cordaDriver "com.r3.corda:corda-shell:4.9"
    ```
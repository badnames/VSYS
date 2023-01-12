package dslab.util.parser;

import java.util.List;

public interface IDMTPParserListener {
    void onBeginCommand();

    void onQuitCommand();

    void onToCommand(List<String> recipients);

    void onSubjectCommand(String subject);

    void onFromCommand(String from);

    void onDataCommand(String data);

    void onHashCommand(String hash);

    boolean onSendCommand();
}

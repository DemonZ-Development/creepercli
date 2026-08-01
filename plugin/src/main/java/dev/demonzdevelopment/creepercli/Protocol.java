package dev.demonzdevelopment.creepercli;

public final class Protocol {
    public static final int VERSION = 1;

    public static final String ACTION_PING = "ping";
    public static final String ACTION_AUTH_LOGIN = "auth.login";
    public static final String ACTION_AUTH_LOGOUT = "auth.logout";
    public static final String ACTION_AUTH_WHOAMI = "auth.whoami";
    public static final String ACTION_AUTH_PASSWD = "auth.passwd";
    public static final String ACTION_AUTH_TOTP_SETUP = "auth.totp.setup";
    public static final String ACTION_AUTH_TOTP_VERIFY = "auth.totp.verify";
    public static final String ACTION_AUTH_TOTP_DISABLE = "auth.totp.disable";

    public static final String ACTION_FS_PWD = "fs.pwd";
    public static final String ACTION_FS_LS = "fs.ls";
    public static final String ACTION_FS_CD = "fs.cd";
    public static final String ACTION_FS_TREE = "fs.tree";
    public static final String ACTION_FS_CAT = "fs.cat";
    public static final String ACTION_FS_HEAD = "fs.head";
    public static final String ACTION_FS_TAIL = "fs.tail";
    public static final String ACTION_FS_WC = "fs.wc";
    public static final String ACTION_FS_TOUCH = "fs.touch";
    public static final String ACTION_FS_MKDIR = "fs.mkdir";
    public static final String ACTION_FS_RM = "fs.rm";
    public static final String ACTION_FS_CP = "fs.cp";
    public static final String ACTION_FS_MV = "fs.mv";
    public static final String ACTION_FS_INFO = "fs.info";

    public static final String ACTION_FS_EDIT_LOCK = "fs.edit.lock";
    public static final String ACTION_FS_EDIT_PUSH = "fs.edit.push";
    public static final String ACTION_FS_EDIT_UNLOCK = "fs.edit.unlock";

    public static final String ACTION_FS_GREP = "fs.grep";
    public static final String ACTION_FS_FIND = "fs.find";

    public static final String ACTION_XFER_PUSH_START = "xfer.push.start";
    public static final String ACTION_XFER_PUSH_CHUNK = "xfer.push.chunk";
    public static final String ACTION_XFER_PUSH_FINISH = "xfer.push.finish";
    public static final String ACTION_XFER_PULL_START = "xfer.pull.start";
    public static final String ACTION_XFER_PULL_CHUNK = "xfer.pull.chunk";
    public static final String ACTION_XFER_PULL_FINISH = "xfer.pull.finish";
    public static final String ACTION_XFER_ABORT = "xfer.abort";
    public static final String ACTION_XFER_LIST = "xfer.list";

    public static final String ACTION_EXEC_RUN = "exec.run";

    public static final String ACTION_MONITOR_STATS = "monitor.stats";
    public static final String ACTION_MONITOR_TPS = "monitor.tps";
    public static final String ACTION_MONITOR_TOP = "monitor.top";
    public static final String ACTION_MONITOR_LOG_START = "monitor.log.start";
    public static final String ACTION_MONITOR_LOG_STOP = "monitor.log.stop";

    public static final String EVENT_LOG_LINE = "log.line";

    public static final String ERR_UNAUTHORIZED = "E_UNAUTHORIZED";
    public static final String ERR_AUTH_FAILED = "E_AUTH_FAILED";
    public static final String ERR_TOTP_REQUIRED = "E_TOTP_REQUIRED";
    public static final String ERR_BANNED = "E_BANNED";
    public static final String ERR_RATE_LIMITED = "E_RATE_LIMITED";
    public static final String ERR_SESSION_EXPIRED = "E_SESSION_EXPIRED";
    public static final String ERR_INVALID_PARAMS = "E_INVALID_PARAMS";
    public static final String ERR_BAD_REQUEST = "E_BAD_REQUEST";
    public static final String ERR_PATH_ESCAPE = "E_PATH_ESCAPE";
    public static final String ERR_NOT_FOUND = "E_NOT_FOUND";
    public static final String ERR_IS_DIRECTORY = "E_IS_DIRECTORY";
    public static final String ERR_NOT_DIRECTORY = "E_NOT_DIRECTORY";
    public static final String ERR_ALREADY_EXISTS = "E_ALREADY_EXISTS";
    public static final String ERR_FORBIDDEN = "E_FORBIDDEN";
    public static final String ERR_IO = "E_IO";
    public static final String ERR_LOCKED = "E_LOCKED";
    public static final String ERR_ALLOWLIST_DENIED = "E_ALLOWLIST_DENIED";
    public static final String ERR_TIMEOUT = "E_TIMEOUT";
    public static final String ERR_PAYLOAD_TOO_LARGE = "E_PAYLOAD_TOO_LARGE";
    public static final String ERR_CHECKSUM_MISMATCH = "E_CHECKSUM_MISMATCH";
    public static final String ERR_NO_TRANSFER = "E_NO_TRANSFER";
    public static final String ERR_INTERNAL = "E_INTERNAL";
    public static final String ERR_SERVER_FULL = "E_SERVER_FULL";

    private Protocol() {
    }
}

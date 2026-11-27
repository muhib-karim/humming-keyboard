//! Built-in Model Context Protocol (MCP) JSON-RPC 2.0 endpoint served over `/mcp` on the local HTTP server.
//! Lets Claude Desktop, Cursor, and local coding agents list/read/search offline meetings, inspect local
//! dictation history, and run Humming's text cleanup without cloud uploads or duplicate model processes.

use serde_json::{json, Value};

pub const PROTOCOL_VERSION: &str = "2024-11-05";

pub fn tools_schema() -> Value {
    json!([
        {
            "name": "humming_list_meetings",
            "description": "List saved offline meetings (id, title, started_ms, segment count, whether summary exists).",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "limit": { "type": "integer", "minimum": 1, "maximum": 100 }
                }
            }
        },
        {
            "name": "humming_get_meeting",
            "description": "Get full Markdown notes, synthesized summary, and timestamped speaker transcript for one meeting by id.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "id": { "type": "integer" }
                },
                "required": ["id"]
            }
        },
        {
            "name": "humming_search_meetings",
            "description": "Search notes and timestamped transcripts across all saved offline meetings.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "query": { "type": "string" },
                    "max_hits": { "type": "integer", "minimum": 1, "maximum": 50 }
                },
                "required": ["query"]
            }
        },
        {
            "name": "humming_list_history",
            "description": "List recent local voice dictations (filtered optionally by substring query).",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "limit": { "type": "integer", "minimum": 1, "maximum": 100 },
                    "query": { "type": "string" }
                }
            }
        },
        {
            "name": "humming_cleanup_text",
            "description": "Clean raw dictated text with Humming's offline filler, self-correction, list, and per-app dictionary rules.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "text": { "type": "string" },
                    "app": { "type": "string" }
                },
                "required": ["text"]
            }
        }
    ])
}

fn ok_resp(id: Value, result: Value) -> (u16, Value) {
    (200, json!({ "jsonrpc": "2.0", "id": id, "result": result }))
}

fn err_resp(status: u16, id: Value, code: i64, msg: impl Into<String>) -> (u16, Value) {
    (
        status,
        json!({ "jsonrpc": "2.0", "id": id, "error": { "code": code, "message": msg.into() } }),
    )
}

pub fn dispatch<F>(raw_body: &[u8], mut exec: F) -> (u16, Value)
where
    F: FnMut(&str, &Value) -> Result<String, String>,
{
    let req: Value = match serde_json::from_slice(raw_body) {
        Ok(v) => v,
        Err(e) => return err_resp(400, Value::Null, -32700, format!("Parse error: {e}")),
    };
    let id = req.get("id").cloned().unwrap_or(Value::Null);
    let Some(method) = req.get("method").and_then(|m| m.as_str()) else {
        return err_resp(400, id, -32600, "Invalid Request: missing method");
    };
    let params = req.get("params").cloned().unwrap_or_else(|| json!({}));

    match method {
        "initialize" => ok_resp(
            id,
            json!({
                "protocolVersion": PROTOCOL_VERSION,
                "capabilities": { "tools": { "listChanged": false } },
                "serverInfo": { "name": "humming", "version": env!("CARGO_PKG_VERSION") }
            }),
        ),
        "notifications/initialized" => (202, json!({})),
        "ping" => ok_resp(id, json!({})),
        "tools/list" => ok_resp(id, json!({ "tools": tools_schema() })),
        "tools/call" => {
            let Some(name) = params.get("name").and_then(|n| n.as_str()) else {
                return err_resp(400, id, -32602, "Invalid params: missing tool name");
            };
            let args = params.get("arguments").cloned().unwrap_or_else(|| json!({}));
            match exec(name, &args) {
                Ok(text) => ok_resp(
                    id,
                    json!({
                        "content": [{ "type": "text", "text": text }],
                        "isError": false
                    }),
                ),
                Err(msg) => ok_resp(
                    id,
                    json!({
                        "content": [{ "type": "text", "text": msg }],
                        "isError": true
                    }),
                ),
            }
        }
        other => err_resp(200, id, -32601, format!("Method not found: {other}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn initializes_lists_and_calls_tools() {
        let (st, init) = dispatch(br#"{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}"#, |_, _| unreachable!());
        assert_eq!(st, 200);
        assert_eq!(init["result"]["protocolVersion"], PROTOCOL_VERSION);
        assert_eq!(init["result"]["serverInfo"]["name"], "humming");

        let (st_n, _) = dispatch(br#"{"jsonrpc":"2.0","method":"notifications/initialized"}"#, |_, _| unreachable!());
        assert_eq!(st_n, 202);

        let (st_p, ping) = dispatch(br#"{"jsonrpc":"2.0","id":2,"method":"ping"}"#, |_, _| unreachable!());
        assert_eq!(st_p, 200);
        assert_eq!(ping["id"], 2);

        let (st_l, list) = dispatch(br#"{"jsonrpc":"2.0","id":3,"method":"tools/list"}"#, |_, _| unreachable!());
        assert_eq!(st_l, 200);
        assert_eq!(list["result"]["tools"].as_array().unwrap().len(), 5);

        let (st_c, call) = dispatch(
            br#"{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"humming_search_meetings","arguments":{"query":"Android"}}}"#,
            |name, args| Ok(format!("{name}:{}", args["query"].as_str().unwrap_or(""))),
        );
        assert_eq!(st_c, 200);
        assert_eq!(call["result"]["isError"], false);
        assert_eq!(call["result"]["content"][0]["text"], "humming_search_meetings:Android");

        let (_, err_call) = dispatch(
            br#"{"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"unknown"}}"#,
            |name, _| Err(format!("unknown tool {name}")),
        );
        assert_eq!(err_call["result"]["isError"], true);
        assert!(err_call["result"]["content"][0]["text"].as_str().unwrap().contains("unknown tool"));
    }

    #[test]
    fn handles_parse_and_method_errors() {
        let (st_p, parse_err) = dispatch(b"{not-json", |_, _| Ok(String::new()));
        assert_eq!(st_p, 400);
        assert_eq!(parse_err["error"]["code"], -32700);

        let (st_m, meth_err) = dispatch(br#"{"jsonrpc":"2.0","id":9,"method":"bogus"}"#, |_, _| Ok(String::new()));
        assert_eq!(st_m, 200);
        assert_eq!(meth_err["error"]["code"], -32601);

        let (st_par, par_err) = dispatch(br#"{"jsonrpc":"2.0","id":10,"method":"tools/call","params":{}}"#, |_, _| Ok(String::new()));
        assert_eq!(st_par, 400);
        assert_eq!(par_err["error"]["code"], -32602);
    }
}

package com.openclaw.android.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import kotlinx.serialization.json.*

class ContactsTool(private val context: Context) : Tool {

    override val name = "contacts"

    override val requiredPermissions = listOf(Manifest.permission.READ_CONTACTS)
    override val description = "Search contacts, get contact details, or initiate a call/text. " +
            "Actions: 'search' (find contacts by name), 'call' (start a phone call), 'text' (open SMS)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("search"); add("call"); add("text") })
                put("description", "Action to perform")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Contact name to search for")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number for call/text action")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Message body for text action")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'query' for search")
                    searchContacts(query)
                }
                "call" -> {
                    val number = args["phone_number"]?.jsonPrimitive?.contentOrNull
                        ?: findPhoneForName(args["query"]?.jsonPrimitive?.contentOrNull ?: "")
                        ?: return ToolResult.error("Provide 'phone_number' or a 'query' name to call")
                    initiateCall(number)
                }
                "text" -> {
                    val number = args["phone_number"]?.jsonPrimitive?.contentOrNull
                        ?: findPhoneForName(args["query"]?.jsonPrimitive?.contentOrNull ?: "")
                        ?: return ToolResult.error("Provide 'phone_number' or a 'query' name to text")
                    val message = args["message"]?.jsonPrimitive?.contentOrNull ?: ""
                    openSms(number, message)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Contacts permission not granted. Please grant contacts access in Settings.")
        } catch (e: Exception) {
            ToolResult.error("Contacts error: ${e.message}")
        }
    }

    private fun searchContacts(query: String): ToolResult {
        val resolver = context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
        )

        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, selection, selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
        ) ?: return ToolResult.success("No contacts found for '$query'.")

        val sb = StringBuilder("Contacts matching '$query':\n\n")
        val seen = mutableSetOf<String>()
        var count = 0

        cursor.use {
            while (it.moveToNext() && count < 20) {
                val name = it.getString(0) ?: "Unknown"
                val number = it.getString(1) ?: ""
                val typeInt = it.getInt(2)
                val type = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                    context.resources, typeInt, "Other"
                )
                val key = "$name|$number"
                if (key !in seen) {
                    seen.add(key)
                    sb.append("- **$name**: $number ($type)\n")
                    count++
                }
            }
        }

        // Also fetch email addresses for matched contacts
        val emailCursor = resolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Email.ADDRESS,
            ),
            "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            null
        )
        emailCursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0) ?: "Unknown"
                val email = it.getString(1) ?: continue
                val emailKey = "$name|$email"
                if (emailKey !in seen) {
                    seen.add(emailKey)
                    sb.append("- **$name**: $email (Email)\n")
                }
            }
        }

        if (count == 0 && seen.isEmpty()) sb.append("No contacts found.")
        return ToolResult.success(sb.toString())
    }

    private fun findPhoneForName(name: String): String? {
        if (name.isBlank()) return null
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun initiateCall(number: String): ToolResult {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(number)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolResult.success("Opening phone dialer for $number")
    }

    private fun openSms(number: String, message: String): ToolResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(number)}")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolResult.success("Opening SMS to $number" + if (message.isNotBlank()) " with message draft" else "")
    }
}

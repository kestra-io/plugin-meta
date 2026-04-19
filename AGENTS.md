# Kestra Meta Plugin

## What

- Provides plugin components under `io.kestra.plugin.meta`.
- Includes classes such as `WhatsAppIncomingWebhook`, `WhatsAppExecution`, `WhatsAppTemplate`, `MessagingType`.

## Why

- What user problem does this solve? Teams need to automate Meta (Facebook and Instagram) publishing with the Graph API from orchestrated workflows instead of relying on manual console work, ad hoc scripts, or disconnected schedulers.
- Why would a team adopt this plugin in a workflow? It keeps Meta steps in the same Kestra flow as upstream preparation, approvals, retries, notifications, and downstream systems.
- What operational/business outcome does it enable? It reduces manual handoffs and fragmented tooling while improving reliability, traceability, and delivery speed for processes that depend on Meta.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `meta`

Infrastructure dependencies (Docker Compose services):

- `app`

### Key Plugin Classes

- `io.kestra.plugin.meta.facebook.posts.Create`
- `io.kestra.plugin.meta.facebook.posts.Delete`
- `io.kestra.plugin.meta.facebook.posts.GetInsights`
- `io.kestra.plugin.meta.facebook.posts.List`
- `io.kestra.plugin.meta.facebook.posts.Schedule`
- `io.kestra.plugin.meta.instagram.media.CreateCarousel`
- `io.kestra.plugin.meta.instagram.media.CreateImage`
- `io.kestra.plugin.meta.instagram.media.CreateVideo`
- `io.kestra.plugin.meta.instagram.media.GetInsights`
- `io.kestra.plugin.meta.instagram.media.List`
- `io.kestra.plugin.meta.messenger.MessengerExecution`
- `io.kestra.plugin.meta.whatsapp.WhatsAppExecution`
- `io.kestra.plugin.meta.whatsapp.WhatsAppIncomingWebhook`

### Project Structure

```
plugin-meta/
├── src/main/java/io/kestra/plugin/meta/whatsapp/
├── src/test/java/io/kestra/plugin/meta/whatsapp/
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines

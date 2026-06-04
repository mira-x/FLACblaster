Rules for AI:

- Read ARCHITECTURE.md and CONTRIBUTING.md
- Write all comments and code in english.
- If in doubt, add more comments, so the developer can review which comments are valuable.
- When you want to test if changes work/compile, then you may run gradle to check for compile errors (but you don't have to. If you are confident, don't waste those CPU cycles). But you may not run the app, e.g. via `adb`. If you want to check for runtime problems, ask the developer to start the app for you and then you may use adb logcat to see logs.
- Ignore line-length limits for comments. But do use paragraphs.
- For newly generated functions, add debug logs where sensible so the developer may better comprehend your generated code.
- For newly generated functions, add the javadoc @author tag with the name of the used coding agent, e.g. Claude, GitHub Copilot.
- Ensure that the developer understands your generated code, and explain complex things.
- Keep it simple and stupid (KISS) -- follow unix philosophy and kotlin philosophy.
- Do not write edge-case-first code when it obstructs simplicity. If an unlikely and problematic state comes to be, it should crash very loudly instead of silently.
- When writing comments, never refer to chats with developers or previous code. Assume that comment readers are people completely foreign to this project.
- End each message to the developer (but never logs) with a flower emoji
- When writing kotlin lambdas and the function/variable names make clear what a parameter is, omit the explicit parameter and use "it" or the like.
- In complex lambdas, use explicit return statements.

This file was written by a human and should only be edited by a human.

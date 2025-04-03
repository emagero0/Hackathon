# Contributing to AI-powered ERP Solution

We love your input! We want to make contributing to this project as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## Development Process (Git Flow)

We follow Git Flow for our development process:

### Branch Structure
- `main` - Production-ready code only
- `develop` - Main development branch where features are integrated
- Feature branches - Individual development work

### Branch Naming Convention
Feature branches should follow this naming pattern:
- `backend-*` - Backend team working on API (e.g., `backend-api-integration`)
- `ai-*` - AI team working on ML features (e.g., `ai-document-verification`)
- `frontend-*` - Frontend team working on UI (e.g., `frontend-dashboard`)
- `devops-*` - DevOps team working on infrastructure (e.g., `devops-deployment`)

### Development Workflow
1. Create your feature branch from `develop` following the naming convention
2. Develop and test your changes
3. Keep your branch updated with `develop`
4. If you've added code that should be tested, add tests
5. If you've changed APIs, update the documentation
6. Ensure the test suite passes
7. Make sure your code lints
8. Create a pull request to merge into `develop`

### Merging Process
1. Feature branches → `develop` (requires code review)
2. `develop` → `main` (requires thorough testing and approval)

## Pull Request Process

1. Update the README.md with details of changes to the interface, if applicable
2. Update the documentation with details of any new environment variables, exposed ports, etc.
3. The PR may be merged once you have the sign-off of two other developers

## Any contributions you make will be under the MIT Software License

In short, when you submit code changes, your submissions are understood to be under the same [MIT License](LICENSE) that covers the project. Feel free to contact the maintainers if that's a concern.

## Report bugs using GitHub's [issue tracker](../../issues)

We use GitHub issues to track public bugs. Report a bug by [opening a new issue](../../issues/new); it's that easy!

## Write bug reports with detail, background, and sample code

**Great Bug Reports** tend to have:

- A quick summary and/or background
- Steps to reproduce
  - Be specific!
  - Give sample code if you can
- What you expected would happen
- What actually happens
- Notes (possibly including why you think this might be happening, or stuff you tried that didn't work)

## Use a Consistent Coding Style

* Use 2 spaces for indentation
* You can try running `npm run lint` for style unification

## License

By contributing, you agree that your contributions will be licensed under its MIT License.

## References

This document was adapted from the open-source contribution guidelines for [Facebook's Draft](https://github.com/facebook/draft-js/blob/main/CONTRIBUTING.md).

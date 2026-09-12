# Resume and Portfolio Working Rules

Sources:

- `2026년 이력서, 포트폴리오 리뉴얼` PDF, processed on 2026-06-10.
- `전자책_리뷰_이벤트_시리즈1_전자책.pdf`, processed on 2026-06-10.

## Core Stance

The reader is a busy interviewer or hiring engineer. They may review many resumes in one sitting while also handling interviews, assignments, coding tests, and daily work. Every resume and portfolio decision should reduce their reading burden and help them judge fit quickly.

The goal is not to look passionate. The goal is to look credible, useful to the team, and easy to evaluate.

## Resume Strategy

- Avoid relying only on a platform resume format. Use a custom resume made in Google Docs, Word, Notion, Figma, or Canva, then submit it directly where possible.
- Keep the resume skimmable. For junior or early-career candidates, aim around 2 pages with 3 to 4 strong projects. Two projects can work only when both are unusually strong.
- Use fact-based wording. Avoid vague identity claims like passion, sincerity, responsibility, or diligence unless backed by evidence.
- Put the most convincing evidence first. The interviewer reads top to bottom.
- The resume is the document that is always read. The portfolio is an additional document read when the interviewer wants detail. Therefore the resume must contain the full compressed story by itself.

## Resume Top Section

- Title: keep it plain, usually `Name Resume`. Do not add decorative slogans.
- Photo: generally include one. Match the photo style to the company culture: formal for conservative SI/finance, more natural for service/startup teams.
- Motivation: write 2 to 3 lines only when there is a real connection between the JD and the candidate's project experience. Frame it as: because I have done X, I can contribute Y to your team.
- Introduction: make it fact-based and question-inducing. One strong line should make the interviewer ask about a specific project or architecture.
- Profile: include name, email, career/education status, GitHub, blog, and portfolio link. Do not include home address.
- Keep information/contact fields minimal. Remove anything that can create unnecessary bias before the interview, such as home address or irrelevant personal details.
- Career/education status should be clear on the first page so the interviewer immediately understands the candidate's level.
- Blog/GitHub are supporting signals, not the main proof. Keep GitHub public where possible and structurally normal. Blog cadence does not need to be excessive.

## Resume Project Section

For junior candidates, organize by project. For candidates around 5+ years, organize by company or major project area.

Each project should include:

- Descriptive project title that explains the service or purpose, not just a codename.
- Date by month, not day. When possible, organize work into meaningful project periods around 3 months rather than tiny fragments.
- Core tech stack only. Omit generic collaboration tools like Git, Slack, and Notion unless unusually relevant.
- Include versions for important technologies, such as Java 17 or MySQL 8.0.
- Team size by role, such as `FE 2 / BE 3`, because same-position collaboration matters.
- One-line service overview.

Project bullets:

- Start with 3 to 4 strongest problem-solving bullets.
- Each key bullet should answer: domain/function, problem, solution, result.
- After problem-solving bullets, include 2 to 3 implementation bullets if needed, but still include the technology used and the effect.
- End with adjacent strengths relevant to the target role, such as infra, monitoring, deployment, FE collaboration, or system operations.
- Remove phrases like `what I contributed`; the resume is already about the candidate's work.
- For juniors, roughly 60% of project content should prove fundamentals and 40% should show distinctive strengths.
- Upgrade weak implementation bullets with the formula: what technology, how it was applied, and how much impact it made.
- Avoid leaving bullets as `feature development` or `CRUD implementation` when a clearer technical angle or measurable improvement can be found.
- If one long job or service feels like a single project, split it into meaningful project chunks such as performance improvement, maintenance, new feature build, platform work, or operations improvement.
- For employed candidates, prefer improving and measuring real work-related features locally before inventing unrelated personal projects. For non-employed candidates or candidates with too little material, build a targeted personal service aligned to target JDs.

Good backend fundamentals to surface:

- Query plan analysis and query/index improvement.
- Local cache or Redis cache with consistency considerations.
- Sync vs async processing and performance impact.
- Transaction boundaries, data consistency, locking, and layered design.
- Deployment, monitoring, failure handling, and test environment setup when relevant.

Distinctive backend strengths can include:

- JVM memory or GC analysis and tuning.
- Network issue analysis with tools such as tcpdump.
- Kafka/event architecture design.
- SSE or real-time event delivery architecture.
- Scalability, bottleneck analysis, and measured performance improvement.

## Certificates, Awards, Activities

- Certificates usually do not differentiate candidates for startups or service companies.
- Information Processing Engineer can matter for SI and some finance roles.
- Awards and activities matter more by depth than count. One deep, relevant experience beats many shallow entries.

## Portfolio Strategy

The portfolio is not a duplicate resume and not a slide deck filled with app screenshots. It expands selected resume bullets.

Process:

- From each resume project, collect the one-line problem-solving bullets.
- Choose about 4 strong cases from all candidates, or 4 to 5 if needed.
- Select cases that can be explained visually from beginning to end. If it cannot be diagrammed, it may be too weak for the portfolio.

Each portfolio case should use:

- Title: the resume's compressed problem-solving bullet.
- Diagram: architecture, sequence diagram, data flow, before/after architecture, or test result. For backend, show structure and flow, not just UI screenshots.
- Problem: about 3 concise lines.
- Solution: about 3 concise lines.
- Result: about 3 concise lines.

Diagram style:

- Use simple rectangles, arrows, and clear labels.
- Text labels like `Application`, `Docker`, `Master DB`, `Read DB` are often better than decorative product icons.
- draw.io and Excalidraw are suitable.
- Keep the same case structure throughout the portfolio.

Evidence:

- Prefer measured results: response time, TPS, latency, error rate, bottleneck, CPU/memory, environment specs.
- Include performance test context when possible: cores, memory, load level, bottleneck, and before/after numbers.
- Do not paste code screenshots. Mention key annotations, APIs, or keywords only when they help explain the flow.
- Do not duplicate resume-style personal information at the top of the portfolio. The portfolio assumes the resume has already been read.
- Add a short `Proficiency` or technical capability section at the portfolio top, targeted to the company JD. It should say what the candidate can immediately do for the team.
- For backend candidates, CMS/admin-system capability can be useful: simple React/Vue plus backend ability may matter in startups or service companies where internal tools are maintained by backend/junior engineers.
- Service screenshots or product UI images can be included only as a low-priority bottom section. They should not replace architecture, data flow, sequence, or performance evidence.
- Communication/documentation/leadership can be a separate low-priority section when it would otherwise repeat across projects. If the candidate lacks work experience, this section can move higher.

If the candidate lacks obvious problem-solving stories:

- Re-run existing projects locally if possible.
- Ask: what would break at around 1000 TPS?
- List bottlenecks, improve one or more, measure, and turn that into resume/portfolio material.

## Self-Introduction and Application Essays

- Split long answers into 500-character blocks.
- Each 500-character block should use about a 50-character subtitle plus 450 characters of body.
- Each block should map to a distinct JD requirement or preferred qualification.
- Subtitle types:
  - A project or experience proves I can help the team with X.
  - A project or experience shows I grew in Y.
- The reader should grasp the core from subtitles alone.

## Personal Weapons

Use one intentionally placed, high-quality differentiator.

Blog weapon:

- Place a blog link where it interrupts the resume flow enough to be noticed, often between Introduction and Personal Information.
- Use a title that creates curiosity, but only if the article fully delivers.
- One deep article is enough.
- Prefer common, widely useful topics such as Redis, MySQL, Kafka, caching, transactions, or performance.
- A strong topic can cover a perceived weakness. Example: if the candidate is moving from SI/Oracle-heavy work to service companies, write a deep MySQL article.
- Do not optimize for post count. A few deep articles are stronger than many shallow ones.
- A strong article may take weeks, not hours. A useful cadence can be 3 to 4 substantial articles per year.

GitHub weapon:

- Treat GitHub and Blog as extensions of the resume.
- The GitHub profile should be simple: recognizable profile photo, name, career/status, and one or two weapon links.
- Avoid flashy profile README decoration, animations, excessive icons, or unrelated self-expression.
- Put the strongest blog article or open-source contribution in the README so it is visible immediately.
- Open-source contribution is a strong differentiator when it includes real logic changes, spec changes, or feature additions.
- Avoid `1 day 1 commit` as a display tactic. If commits exist, make them meaningful and public where possible.

Interview weapon:

- Design the resume introduction to trigger the first question.
- Prepare to answer with an architecture explanation.
- If appropriate, offer to draw the architecture on a whiteboard or tablet.

## Application Strategy

- Prefer direct application through the company website when possible.
- Do not rely on one common resume for every company. Build a company-targeted version when the opportunity matters.
- Add a short company-specific motivation/contribution paragraph of 2 to 3 lines, using both business and technical relevance when possible.
- Direct application lets the candidate submit a custom resume and often answer company-specific questions, which can create differentiation.
- If using a platform, fill platform fields sincerely even when content overlaps with the resume, and also attach or link the custom resume/portfolio.

## AI-Era Career Strategy

- Reduce dependence on any single company by building independent earning or distribution, even a tiny amount.
- Do not chase every new AI tool as the main strategy.
- Study stable fundamentals and common concepts that outlast tool changes.

## Layout Notes

- Resume and portfolio can be clean vertical documents rather than flashy horizontal slide decks.
- A simple white layout with blue section headings, thin dividers, and clear hierarchy works.
- Resume top example: photo block on the left, Introduction on the right, Blog Link below, Personal Information next.
- Project section example: title, schedule, tech stack, team size, service overview, then bullets.
- Portfolio example: title, visual diagram, then grouped bullets for problem, solution, and result.

## Drafting Checklist

Before drafting:

- Target JD or company.
- Candidate level and target role.
- Projects, dates, tech versions, team composition, and service overview.
- For each project: domain/function, problem, solution, result, and evidence.
- Available metrics or test results.
- GitHub, blog, portfolio, and article links.
- GitHub README weapon links.
- Portfolio top `Proficiency` bullets targeted to the JD.
- Company-specific 2 to 3 line motivation/contribution statement.
- Candidate photo preference and target company culture.

Reject or revise:

- Empty personality claims.
- Tool lists that do not prove role fit.
- Unmeasured "improved performance" claims when numbers can be obtained.
- UI screenshots as backend portfolio proof.
- Code screenshots.
- Long prose paragraphs without scan points.
- Unsupported motivation for a company with no real project/JD connection.

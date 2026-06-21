---
title: Running and Tracking Tests
---

Execute test cases and track results in real-time using SpeQA's test run interface.

## Starting a Test Run

### From the Test Case Editor

1. Open any `.tc.md` test case file
2. In the editor header, click the green **Play** button (or press a keyboard shortcut if configured)
3. SpeQA opens the **Test Run Panel** showing the first step

### From the Project View

1. Right-click a `.tc.md` file in your project
2. Select **Run Test Case**
3. The Test Run Panel opens

## The Test Run Interface

The Test Run Panel displays:

- **Test Case Title** at the top
- **Current Step** with the action and expected result
- **Step Counter** (e.g., "Step 1 of 4")
- **Navigation buttons** to move between steps
- **Result buttons** for marking each step

## Recording Test Results

For each step, choose one of three outcomes:

### Passed
Click the **Passed** button (or green checkmark) when the expected result occurs.
- Example: You entered "john@example.com" in the email field and it appears correctly

### Failed
Click the **Failed** button (or red X) when the expected result does NOT occur or an error happens.
- Example: You clicked "Sign In" but got an error message instead of logging in

### Skipped
Click the **Skipped** button when you cannot test this step (e.g., prerequisites weren't met).
- Example: Network is down, can't reach the server

## Adding Comments

After marking a step as Passed, Failed, or Skipped, you can add a comment:

1. A comment field appears below the result buttons
2. Type your observation (optional but recommended)
3. Comments are especially important for Failed steps to document what went wrong

Examples of good comments:
- **Failed step**: "Got 'Invalid email format' error even though email looks valid"
- **Passed step**: "Field accepted the email without error"
- **Skipped step**: "Server was down during this step"

## Moving Between Steps

Use the navigation buttons to:

- **Next** - Move to the next step
- **Previous** - Go back to review or change a result
- **Jump to Step** - Click the step counter to jump to a specific step

You can change results for previous steps if needed.

## Completing the Test Run

After marking the final step:

1. Review all results (optional)
2. Click **Save Test Run** or **Finish**
3. SpeQA saves the run as a `.tr.md` file in your project's `test-runs/` folder

The test run file includes:
- All step results (Passed, Failed, Skipped)
- Your comments for each step
- Timestamp of when the test was run
- Overall test status

## Viewing Test Run History

### Recent Test Runs
1. Open the test case file (`.tc.md`)
2. Look for the **Test Runs** section in the editor
3. See a list of recent runs with dates and overall results

### Detailed Results
1. Open a `.tr.md` test run file directly
2. View all recorded results, comments, and metadata
3. You can edit comments after the fact (e.g., to add more details)

## Test Run Statistics

SpeQA tracks:

- **Total test runs** - How many times this test case was executed
- **Pass rate** - Percentage of runs that passed all steps
- **Failed step analysis** - Which steps fail most often
- **Execution time trends** - Is testing faster or slower over time?

Access statistics from:
- The test case editor's **Statistics** tab
- The project dashboard (if available in your IDE version)

## Attachments During Test Runs

While running a test, you can attach evidence:

1. After marking a step result, click **Add Attachment**
2. Choose **Screenshot**, **File**, or **Video**
3. For screenshots:
   - SpeQA can capture your screen
   - Paste from clipboard
   - Or browse for an image file
4. The attachment is linked to that specific step in the test run

This is useful for documenting failures or unexpected UI states.

## Handling Multiple Test Cases

To run several test cases in sequence:

1. Run the first test case normally
2. After saving, navigate to another test case file
3. Start a new test run for that case
4. Repeat for each test case

Or use a test suite (if your project defines one) to run multiple tests in order.

## Best Practices

### Test in Order
- Follow the steps in the exact order they appear
- Don't skip steps unless absolutely necessary
- This reflects real user behavior

### Document Failures
- Always add comments to failed steps
- Include:
  - What you expected to see
  - What actually happened
  - Any error messages
  - What you were doing when it failed

### Run Regularly
- Run tests after code changes
- Re-run failed tests to confirm fixes
- Maintain historical records for trend analysis

### Keep Evidence
- Attach screenshots of failures
- Save error messages as comments
- This helps developers debug issues

## Troubleshooting

**Test Run Panel doesn't open:**
- Make sure you're in a `.tc.md` file
- Check that SpeQA is installed (see [Installation](./installation.md))
- Try right-clicking the file and selecting "Run Test Case"

**Can't mark a step as Passed/Failed:**
- Make sure you're on the step you want to mark
- Look for the result buttons in the panel
- Some IDEs may require clicking the step first

**Test run wasn't saved:**
- Always click **Save Test Run** or **Finish** at the end
- Without saving, your results are lost
- Check the `test-runs/` folder to confirm the file was created

**Can't find my test run file:**
- Test runs are saved in the project's `test-runs/` folder
- They're named like `test-case-name-YYYY-MM-DD.tr.md`
- Check your project's folder structure

## What's Next?

- Learn about [Advanced Features](./advanced-features.md) to enhance test evidence
- Set up [Claude Code Skills](./claude-code-skills.md) to write more test cases
- Review your test runs to identify patterns in failures

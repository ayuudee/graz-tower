**Testing: Principles and Standards**

Summary
===============================================================================

This document describes the principles and standards we use to test the software
we write on this project. Prefer principles over standards and use this document
as the basis to write and review testing code.

Principles
===============================================================================

1. **Tests are the most important code we write.** Treat tests with more care
   and rigour than the _production_ code under test. They should show the very
   limit of our ability to write clean and expressive code.

2. **Prefer system-level and integration tests above unit tests.** While we always
   want tests to be focused, try and exercise as much of the relevant system as
   you can. You want to clearly show the context of whatever is under test.

3. **Use the type system to make the "impossible" impossible.** If you're
   reading this, you already know what this means, but also recognise that
   many/most unit tests can be replaced with more meaningful types.

4. **Unit test when you have to.** Keep unit tests that test key algorithms for
   which there is a broad, formal, independent oracle of correctness; and/or for
   which there is ascribable business value [#Coplien].

5. **If in doubt, throw it out.** If you can't clearly see the business value
   in a particular test, do your best to have it expunged (or be convinced of
   its value).

6. **Good design it testable, but good tests don't make good design**. Remember
   that testing is no substitute for robust technical process. If you think
   something is brittle or weird, it probably is.

7. **Don't just test where its easy.** Testing orchestration code is hard.
   Testing the UI is hard. The obstacle is the way.

References
===============================================================================

[#Coplien]: James O Coplien. [Why unit testing is a waste](http://rbcs-us.com/documents/Why-Most-Unit-Testing-is-Waste.pdf)


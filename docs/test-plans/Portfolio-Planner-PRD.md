Document reference code (derived from product-module-sub-module)
#ticket number: Portfolio Planner Flow
14-Jul-2026


Objective
The Portfolio Planner turns a few simple inputs from an investor into a ready investment plan they can act on inside the Advisor App. This document specifies the full screen-by-screen flow for the three planner options, invest for higher returns, invest for monthly income and invest to save taxes, for both Fund Advisor subscribers and free-account users. 
What has changed: The investment plan is now portfolio aware and reuses suitable funds the investor already owns, though the user can opt to see a new plan without referring to his existing investments. The front-end changes, highlighted in yellow in this version, are mostly on the Investment plan output page. 
All the changes/additions to this version of the PRD are highlighted in yellow. Deletions from this version of PRD are striked-off in red.

Scope:
Platform: Value Research Fund Advisor mobile app. The web version of the Portfolio Planner to be updated based on this flow, but in its own design language.
Screens: Select Investor, Change Risk Assessment, KYC Pending, KYC Under Process, Risk Assessment Pending, the three Portfolio Planner option screens, Invest for higher return form and its variants (lumpsum only, SIP only, lumpsum+SIP), Invest for monthly income form with its follow-up fields, Invest to save tax with its follow-up fields, Investment Plan output page along with its variants, Investment Breakdown, Edit Investment Plan and the Review page.
Users: Fund Advisor subscribers and free-account users. Within subscribers, investors both with and without an existing portfolio, and investors at every KYC and risk assessment state.

Out of Scope:
Explicitly list what this PRD does NOT cover, even if it seems obvious. This prevents assumptions that silence = inclusion.
This is a loose definition of ‘Out of scope’. Please describe what to cover in this section more precisely. Going by the above definition, this list of out of scope can be endless. For example, KYC and onboarding, Mutual Fund Insight, Wealth Insight, Stock Advisor, our data services, HR policies, etc., to start with.


Version History
Once the PRD is finalised, all the further changes need to be recorded 
Version #
Date
Created/ Revised by
Reason for change
1.0
Not available
Ashutosh Gupta
Original version
2.0
17-Feb-2026
Ashutosh Gupta
Stopped showing ‘Potential value’ on input page
3.0
31-Jul-2026
Ashutosh Gupta
Making the investment plan portfolio aware




Stakeholders
List of people involved and their roles
Name
Role/Domain
Responsibility
Sign-off
Ashutosh Gupta
Product Manager
Overall Product Guidance
04-Aug-2026
 Ranjesh Kumar Gupta
Solution Architect
Architecture and dev guidance
07-08-2026
 Arpit Srivastava
UX/UI
Wireframes and prototypes
06-08-2026 
 Akhil Sharma
Marketing (optional)
GTM and communication-
Pending
 Sahib Taneja
CS (optional)
Support readiness and FAQs-
06-08-2026


 Alok Nath
SEO (optional)
Input on SEO-friendly content, review the UX & UI and sign-off during UAT, etc
Not required



Acceptance Criteria
Every condition below must hold for the feature to be considered complete. The screen behaviour, the calculations and all message strings are specified inline in the User Journey section. This list is the sign-off checklist, and each item points back to that section for detail.
All buttons and links on every screen should work as expected, per the document.
Appropriate screens and CTAs should be shown to different users as per hte document.
The Select Investor screen appears for Fund Advisor subscribers only. Free users go straight to the planner options screen.
The KYC Pending, KYC Under Process and Risk Assessment Pending states each render as specified and route the user to the correct journey with the same investor preselected.
On all three planner option screens, "Show investment plan" replaces "Calculate" and Potential Value is no longer displayed. Clicking it loads the plan directly on the next screen.
The financial year question on Invest to save taxes appears only between 1 January and 31 March, and the financial years move forward by one every year.
The portfolio-aware plan loads by default for an investor who has an existing portfolio. The toggle is not shown for an investor with no existing portfolio, and the new plan loads instead.
The count of funds picked from the existing portfolio and the "Already yours" labels appear only on the "Includes Your Funds" tab, and treat plan and option variants of the same fund as already owned.
The plan headline, the investment summary line, the View split dropdown and the first-month totals each render the correct variation for every input combination listed in the User Journey.
Deviation values compute as Modified minus Original, and are colour coded only beyond plus or minus 5 per cent, green for an increase and red for a decrease.
Every validation error fires on the stated field with the stated message. Invest Now stays disabled until the disclaimer is ticked and every field is valid.
The ‘Exit’ fund message notifies the users without blocking them, while a fund suspended for sale does block.
The transaction account inactive and under-process alerts appear and route as specified. An active account initiates the cart order journey.
For a free user, the plan is generated on a Moderate risk profile and shows the real number of funds, with fund names, amounts, instalments, categories and the recommendation rationale masked, and the full disclaimer reachable through Read more.
There SHOULD NOT be any changes to the cart transaction journey as a result of changes implemented under this version of PRD. The cart journey and its emails, notifications, etc. should continue as it is.
There SHOULD NOT be any changes to the user states in respect of KYC and risk assessment as a result of changes implemented under this version of PRD. All their variations should continue as it is from the previous version of this PRD.
Tracking must be implemented as specified in the Tracking Details section below.

Tracking Details (GTM & Reports)
Click events to be put at: 
6 Goals lead-ins at homepage along with ‘No goal, Start investing’ lead-in
Risk Profile Update button at select investor screen
SIP, One-time, both pills at portfolio planner input screen
Edit button on your investment plan screen
Includes your funds/ see a new plan pill on your investment screen
Confirm button on calendar when user edits his SIP date
Primary CTAs on all screens


Page-view event:
Select Investor Screen
Portfolio Planner input screen
Your investment plan


All the edits which the user has made to his originally suggested plan needs to be stored, such as investment amount, number of SIP instalments, changing a fund, deleting a fund and adding a fund. 

Filtering based on platform - App & Web should be available on both GA and Grafana.



User Journey 
The screen-by-screen flow follows. For each screen it states what the user sees, what happens on each action, and the variation between a Fund Advisor subscriber and a free user. Calculations, reference tables and all message strings sit inline with the screen they apply to, rather than being collected at the end.

Flow (VRFA subscriber):
Select investor -> Risk assessment screen (If applicable) -> Investment objective (if applicable) -> Investing need form (SIP/lumpsun amount, duration, etc.) -> Investment Plan -> Edit Investment plan -> Review Changes -> Onward to cart journey


Flow (Non-VRFA subscriber):
Investment objective (if applicable) -> Investing need form (SIP/lumpsun amount, duration, etc.) -> Investment Plan (masked) -> Onward to VRFA landing page




Select Investor Screen
User can choose the investor or add a new investor (if less than 6 investors added).
Existing Risk assessment, if available will be shown with the option to update. Else, the user will be asked to complete the Risk Assessment first.
Upon clicking on the “update” button then a change risk assessment screen will be displayed.
If you click on the Next button then the portfolio planner options screen will be displayed.

Note: DO NOT show the ‘Select Investor’ screen to users who have a free account. Take them straight to the next screen to choose one of the three Portfolio Planner options.




























Change Risk Assessment Screen
Show the below screen if the user clicks ‘Update’ button provided alongside Risk profile on the above screen






























Clicking Retake assessment will redirect to Risk Assessment module with this investor preselected
Choosing manually will open a box with the details of that risk profile. Upon clicking ‘Confirm’, the risk profile will get  updated. But if he chooses a higher risk profile than his existing one, an additional warning message will be shown and it must be ticked by the user before he can confirm.







KYC pending screen

In case the selected investor’s KYC is pending, show the following screen. Upon clicking ‘Complete KYC now’, redirect him to the Fund Advisor onboarding journey with that investor selected by default on the first screen.






KYC Under Process screen

In case the selected investor’s KYC application is under process, show the following message at the bottom of the page in the same UX as shown alongside. 

KYC in Progress
You can use this feature once your KYC is complete. Please check back later.
[Go Back]

Upon clicking ‘Go Back’, redirect him to the page from where he had come.

Note that the above screen will be shown only till the time KYC application is under process. If for some reason it is rejected, then again we will start showing the KYC Pending screen.



Note: The above screens regarding KYC Pending apply in case of Risk Assessment as well.


Risk assessment pending screen

In case the selected investor’s risk assessment is pending, show the following screen. Upon clicking ‘Complete risk assessment’, redirect him to the first screen of risk assessment with the same investor pre-selected.






Portfolio planner option

There are 3 type of planner option:

Invest for higher returns
Invest for monthly income
Invest to save taxes



Invest for higher returns



Both					 One-time only				SIP only










      				














We need to stop showing Potential Value. Therefore, remove the “Calculate” button and replace it with “Show investment plan” button. Upon clicking it, we will directly move the user to the next screen and load the investment plan.

For free user, the investment plan will remain blurred with a CTA (exactly same as existing UI, so no change).









Invest for monthly income screens







Invest to save taxes

The financial year question in the above screen will appear only between 1 Jan and 31 Mar each year, in line with existing Portfolio Planner conditions live on the web. The financial years will automatically move ahead by one every year.




Invest for higher returns/monthly income/saving taxes for FREE users

In case of free users, the above screens of these three modules will be shown without the investor name at the top (sample image below).





Investment Plan (Invest for higher returns and Invest to save tax) for FUND ADVISOR subscribers

Upon clicking on “Breakdown” button then Investment breakup screen will be displayed

Upon clicking on the “Edit” button, users can edit the investment details. 

Both are covered ahead in the document.
11.1: Show the name of the investor. In case of of a non-Fund Advisor subscriber, investor name field will not be shown.

11.2: Heading (‘Your retirement plan’ in above screenshot) will change depending upon the goal selected by the user. Here are the variations possible:

11.2.1 No goal, Start investing -> Invest for higher returns

Note: The above will also be the fallback headline, in case a headline is not mapped for a goal


11.2.2 Retirement


11.2.3 Child’s education

	
11.2.4 Child’s wedding


11.2.5 Buying a home


11.2.6 Buying a car


11.2.7 Next vacation


11.2.8 Tax saving


11.2.9 Regular income




11.3: Show the investment input provided by the user - SIP, Lumpsum, time horizon.

Possible variations:

11.3.1 Investing for higher returns (SIP only input by the user)


11.3.2 Investing for higher returns (Lumpsum only input by the user)

	
	11.3.3 Investing for higher returns (SIP + Lumpsum inputs by the user)
	

	11.3.4 Tax saving
	

Note: 
i) In case SIP is recommended and the user opted for next financial year on the interface, change the line to “Rs xx,xxx monthly SIP for next financial year”
ii) In case plan recommended is to invest one-time in lumpsum, change the line to: 
Current FY: “Rs xx,xxx one-time for this financial year”
Next FY: “Rs xx,xxx one-time for next financial year” 

	11.3.5 Regular income
	


11.4: Toggle between portfolio aware investment plan or new investment plan



“Include Your Funds’ will be selected by default. This will load the portfolio aware recommendations as per the model.
“See a New Plan” will show recommendations WITHOUT considering existing portfolio. Funds will be straightaway selected from Analyst’s Choice as per the model.

Note: DO NOT show this toggle in case of an investor who does not have an existing portfolio. Simply load the new plan in that case.

11.5: Show a sentence about whether any funds have been picked from his existing portfolio and if yes, then how many. This is NOT to be shown in case “See a New Plan” is selected. Below are the variations possible:

In case one or more suitable funds are picked from existing portfolio:

In the above screenshot, ‘2’ is a dynamic number. It should show the number of funds picked from his existing portfolio. The plan or option variations of a fund should also be considered as picked up from the portfolio. Therefore, if someone has the Regular plan option of a fund which is suitable and we are suggesting its Direct plan, then it should be counted as picked up from the existing portfolio.
Replace ‘funds’ with ‘fund’ if only one fund is picked from the existing portfolio. Therefore, it will read like: “You already own 1 fund suitable for this goal……”

In case none of his existing funds were picked for recommending:




11.6: Show the total investment amount in the first month



This is calculated as:

Total of lumpsum investment suggested to be invested in one shot across all funds
+
Total of first month SIP instalments across all funds, including for lumpsum suggested to be spread over a number of months



11.7: Show the label “Already yours” against funds picked from his existing portfolio. This includes plan and options variants of the same fund.

Note: This is only applicable for “Includes Your Funds” tab. DO NOT show this label in case of “See a New Plan” even if the fund is already in the user’s portfolio.


11.8: The “View split” drop down should be shown ONLY in case of funds where more than one investment is suggested (SIP + SIP; SIP + Lumpsum; Lumpsum + Lumpsum). Upon clicking, it expands as shown below:


=>


Here are the variations possible:

i) Lumpsum invested as lumpsum




ii) Lumpsum spread across a number of months
							
iii) SIP




11.9: Show the details of SIP/lumpsum/No. of months, etc. below the amount. Here are the variations possible:

i) If multiple investments are suggested in the same fund, write “first-month total” and then provide the ‘Split’ dropdown to show the details as explained above. The amount should be the total investment in that fund in the first month (Lumpsum as one-time + first month instalment of SIPs, including for lumpsum spread across a number of months)


ii) Single SIP in the fund


iii) Single lumpsum invested as lumpsum


iv) Single lumpsum suggested to be spread over a number of months

For variant iv), don’t go by what is shown in the screenshot, i.e., “first month . lumpsum spread”. Instead use “lumpsum spread . xx months”

11.10: Show the first two lines of disclaimer and then the ‘Read more’ link, which expands upon clicking to show full disclaimer. No need to show disclaimer in bullet point format. Make a separate paragraph for each bullet as shown in the design below.



Upon clicking ‘Read more’:


Note: The below line is missing in the Advisor App version of disclaimer but it is there in the Advisor web version. Please add it to the app version as well and make it uniform.

The Operator, its directors, employees, and agents may have holding or position in the securities advised herein.

The web version (point 6 is missing from Advisor App version):

Investment plan (Invest for monthly income) for FUND ADVISOR subscribers

Clicking on “Read more” in the blue box will load the entire text in the form of a bottom drawer.

The screenshot alongside is the sample where separate Income portfolio and Growth portfolio have been carved out. In cases where the two separate portfolios are not carved out, there will be only one listing of funds shown. Separate headings for “Your income portfolio” and “Your growth portfolio” will not be there.








Investment Breakdown




















Here are the variations possible:

i) Lumpsum invested as lumpsum


ii) Lumpsum spread across a number of months
						

iii) SIP


Aggregates shown at the bottom:
Monthly SIP Total: This is the total monthly SIP across all funds. Includes ONLY the SIP amount provided by the user. It EXCLUDES the lumpsum input provided by the user which we spread over a number of months
Lumpsum spread: In case the user’s lumpsum input is being spread across several months, this is the total of the first-month instalment of such amounts across funds
One-time this month: The portion of user’s lumpsum input which we are suggesting to invest at one go
First-month investment: Lumpsum input suggested to be invested in one shot + Total of first month SIP instalments across all funds, including for lumpsum suggested to be spread over a number of months

Investment Plan (Invest for higher returns/monthly income/ save tax) for FREE users

In case of free users, we will generate an actual investment plan assuming ‘Moderate’ risk profile. The ‘XX Suitable Mutual Funds’ line will also show the actual number of funds generated in the plan.
However, the names of the funds, amounts, no. of instalments, fund category, etc. will remain hidden for a free user.
The recommendation rationale under “Why are we recommending these?” will also remain masked.
The headline “Your plan for buying a home” will change depending upon the goal chosen.
The second line “Rs xxxx monthly SIP + Rs XXXX one-time….” will also change based upon the actual input provided by the user.
Clicking on the “Unlock a future full of money” box, the ‘Breakdown’ link or the ‘Subscribe now’ button will redirect to the landing page of Fund Advisor.
The Important disclaimer will be shown to a free user in full. We will show 3 lines by default and the ‘Read more’ will expand it to full.



Portfolio Planner - Edit Investment Plan (New feature)

Add an option - ‘Edit’ besides ‘Invest Now’ in the below window.
Remove the ‘Change Fund’ option besides the fund name. 
On clicking ‘Edit’, the below flow follows: 





No. of instalments box in case of one time investment, this will be frozen. #?? 


All other fields will be editable.
On clicking the fund name box, accordion (as shown below) will open
The user will be able to directly change the Amount and No of months
















The functionality to delete a fund from the investment plan is described below:



Changing/ Addition of funds 

When an investment plan is displayed, a fund that has not been recommended directly can be incorporated in two ways. 

Case 1 : Suggested fund is changed and an alternate fund is picked. 




Mapping of funds that need to be shown as alternatives in the Portfolio Planner 
If the user clicks on Change fund on the Edit investment plan page, show ‘Suitable alternatives from Analyst’s Choice’ as per the mapping in below table. Note that this applies only for changing funds recommended by us in our investment plan.

Category of recommended fund
Alternatives in Change Fund Menu
Mid Cap
Mid Cap funds from Analyst's Choice
Small Cap
Small Cap funds from Analyst's Choice
Growth bucket of Analyst’s Choice
Growth bucket funds from Analyst’s Choice
ELSS
ELSS from Analyst's Choice
International equity
International equity funds from Analyst's Choice
Aggressive hybrid
Aggressive hybrid funds from Analyst's Choice
Large cap Active/Nifty 50 index fund/BSE Sensex index fund (excluding ETFs)
Large cap Active/Nifty 50 index fund/BSE Sensex index fund (excluding ETFs) from Analyst's Choice
Large Cap Next 50 index (excluding ETF)
Large Cap Next 50 index funds (excluding ETF) from Analyst's Choice
Equity savings
Equity savings funds from Analyst's Choice
Short duration
Short duration funds from Analyst's Choice
Liquid
Liquid funds from Analyst's Choice
Overnight
Overnight funds from Analyst's Choice
Dynamic Asset Allocation
Dynamic Asset Allocation funds from BUY list and Analyst's Choice list
Conservative Hybrid
Conservative Hybrid funds from BUY list and Analyst's Choice list



Note : If no suggestions are given in the Suitable alternatives section, refer to the design reference given in Case 1.2.









If the recommended fund is changed, three sub-cases will be possible:

Case 1.1 :  If the recommended fund is changed to a fund from the suggested sub-set of Analyst’s choice, for changing this fund hereon the same pop-up is shown. 



Note : Show only the search bar box in case the fund name box doesn't have any Analyst Choice fund


Case 1.2 : Suggested fund is changed to a fund from Analyst’s choice but not part of  Analyst choice suggestion subset. Hereon the following pop-up is shown.





Design reference for ‘Analyst’s Choice funds’ screen : 




Case 1.3 : Suggested fund is changed to a fund not part of  Analyst choice altogether. 

The same flow as case 1.2 follows.


Case 2 : Add fund button is used to add a fund independently


The flow shown below is followed.



Provide a ‘Reset’ Button below the Investment Plan, by clicking on this the original suggested plan will reappear in entirety. 

               Design reference: 







Under the Review pages  provide the ‘Original’ and ‘Modified’ version of investment plan in terms of - 
Total investment (SIP & one time)- This will be Sum of - (Amount X SIP instalments) + Amount for one time investments
The ‘Deviations’ will show figures arrived at by subtracting Original values FROM the Modified values. So, Deviation = Modified minus Original 
The deviation box will be colour coded if the difference between original and modified is beyond +/- 5%. In this case, use GREEN for increased values and RED for a decrease in allocation respectively. 


Asset Allocation - divided into asset classes based on original plan so only the relevant asset classes would appear; below are the possibilities
Only Equity
Only Debt
Equity and Debt
Commodities (in case Gold/Silver funds are added) only if applicable in case the user adds under the modified version. 
The Original asset allocation will be based on the actual suggested plan
The ‘Modified’ asset allocation will be derived based on the Total amount (one time & SIP including future investments) and the below assumed allocations. 
Sr. No.
VR Category
Asset Allocation
Equity allocation
Debt allocation
1
All Equity categories
100%
NA
2
All Debt categories
NA
100%
3
Hybrid: Arbitrage
0%
100%
4
Hybrid: Conservative Hybrid
25%
75%
5
Hybrid: Equity Savings
35%
65%
6
Hybrid: Dynamic Asset Allocation
50%
50%
7
Hybrid: Balanced Hybrid
50%
50%
8
Hybrid: Aggressive Hybrid
75%
25%
9
Hybrid: Multi asset allocation
Should be fund specific; divided in the following asset allocation buckets - 
Equity = Gross equity minus derivatives = Net equity
Debt = Cash & cash equivalents, all debt, others
Commodities = gold, & silver
Other = for everything else


So let’s say somebody invests Rs 16,000 allocates 50:50 ratio in one equity = Rs 8,000 (SIP of Rs 1000 x 8 months) and one Aggressive hybrid = Rs 8000 (SIP of Rs 2000 x 4 months), in that case the allocation will be calculated based on the Total amount (one time & SIP including future investments)
Equity = (8,000 + 0.75*8000)/16000 = 14,000/16000 = 87.5%
Debt - (0.25*8000)/16000 = 12.5%


The ‘Deviations’ will show figures arrived at by subtracting Modified values FROM the Original values. So, Deviation = Modified minus Original


The deviation number will be colour coded if the difference between original and modified beyond +/- 5% change. 
If change is greater than +5%, use GREEN for the increased value
If change is less than -5%, use RED for a decrease in allocation 


In the Disclaimer box, the below should appear: 


I acknowledge that the above investment plan is different from what was recommended to me. It may not align with my investing need or risk tolerance and therefore, should not be considered as investment advice.

  
The disclaimer box should appear only when a change is made by the user in the original plan. Note that this will be ticked by default. 
Design reference attached below : 









The ‘Invest Now’ button will be frozen in the following cases:

The disclaimer checkbox is not ticked.

Design reference for the same: 





There is any issue in the modified portfolio or if any field is not filled properly. So, an error message should be displayed. 

The error message should appear in the occurrence of the fields… as mentioned below
Fund name
Type
Amount
No. of months

                       


List of errors				Design reference (Type/Amount/No. of Months/Blank field)


Type
SIP investment not allowed
One time investment not allowed

Amount
Min SIP: Rs x,xxx
Max SIP: Rs x,xxx
Min one time: Rs x,xxx
Max one time: Rs x,xxx

No. of months
Min No. of months: XX
Max No. of months: XX

Any required field left blank
This is a required field

Fund name
Fund is suspended for sale
This fund is marked as ‘Exit’ (This is just a notification. Don’t stop the user from going ahead.)
This fund is marked as ‘Avoidable’ (This is just a notification. Don’t stop the user from going ahead.)






Alert message on Your Investment plan screen:

“Monthly amount must be greater than or equal to 1000.”

“One-time amount must be greater than or equal to 5000.”

“Please enter period.”

Upon clicking ‘Invest Now’:
If the investor has not created advice-cum-transaction account at Value Research Fund Advisor, show an alert message: “Your Transaction account is inactive.” Upon clicking “Activate now”, the user will be redirected to the journey to create advice-cum transaction account.

If the investor has already completed the advice-cum-transaction journey but the transaction account activation is pending for reasons like KYC under process or bank validation pending, etc., show an alert message: “Your transaction account setup is under process.” Upon clicking “Okay”, the alert goes away and the user remains on the same page.


If the investor has an active advice-cum-transaction account, it will initiate the cart order journey covered in below documents:
The rules related to cart orders (folio creation, date of investment, email triggers, treatment of Your Orders page, treatment in alert icon etc.) are documented in this document: Carting Order Flow - Fund Advisor 
The Advisor app flow in the app UX is documented in this document: Cart Order Journeys - Advisor App



Resources
Links, documents, and references required to implement the PRD.
Portfolio Planner web version - New flow aligned with Advisor App: https://drive.google.com/file/d/1Y3CkEQXN4D4FeXNYCTnSlQ07KEXNIJhg/view?usp=drivesdk

Portfolio Planner Advisor App - Investment Plan output page (Download and open the HTML file. Refer to the mobile version by pressing F12): https://drive.google.com/file/d/1vKnyAYtT2cpL_68NJJ4-WHKY-9T9-_U9/view

Portfolio Planner Advisor App flow (excluding the Investment Plan output page): https://www.figma.com/proto/qmccE5Zr5BOZpYs4X2G6IC/Advisor-App---Master-File?page-id=1776%3A704&node-id=6011-8792&node-type=frame&viewport=425%2C7272%2C0.62&t=Z1tI2EVTQtqH0Lej-1&scaling=min-zoom&content-scaling=fixed&starting-point-node-id=6011%3A8792&show-proto-sidebar=1

Cart Order Flow, Fund Advisor: https://docs.google.com/document/d/14uNgi1Pf7rxbTDl95HL1Ej9N-pwUyjV8NH9_I8wCaEg/edit?tab=t.0#heading=h.6bdsssy87hud

Cart Order Journeys, Advisor App: https://docs.google.com/document/d/1JxOsDD-Jj9q8GRgypPcZDRbse-OXqgVK04eGxcnDxeg/edit?tab=t.pazbp961lr9i

Remarks / Notes
Additional context, open questions, excluded scope or known limitations.
The changes/additions to this version of the PRD are highlighted in yellow.
Deletions from this version of PRD are striked-off in red.

Metrics for Success
Portfolio aware recommendations improve the personalization of investment plans, a key promise of the Portfolio Planner. Portfolio awareness is a common demand from the users of the service.
Most of the Fund Advisor subscribers are not first-time investors. They already have existing investments, and our advice should factor them in.

#include <Date.au3>
#include <GUIConstantsEx.au3>
#include <ie.au3>
#include <MsgBoxConstants.au3>
#include <StaticConstants.au3>
#include <StringConstants.au3>

; Create a GUI for referral id and access code.
Local $gui = GUICreate("Sharp Referral", 230, 160)
Local $referralIdInput = GUICtrlCreateLabel("Referral ID", 8, 10, 135, 17)
Local $referralId = GUICtrlCreateInput("referral id", 8, 30, 215, 21)
Local $accessCodeInput = GUICtrlCreateLabel("Access Code", 8, 60, 135, 17)
Local $accessCode = GUICtrlCreateInput("access code", 8, 80, 215, 21)
Local $idOkBtn = GUICtrlCreateButton("OK", 8, 120, 106, 30)
Local $idCancelBtn = GUICtrlCreateButton("Cancel", 117, 120, 106, 30)
Global $fName = "";

; Display a GUI.
GUISetState(@SW_SHOW, $gui)

; Loop until the user exits.
While 1
    ; Process button click.
    Local $nMsg = GUIGetMsg()
    Switch $nMsg
        Case $GUI_EVENT_CLOSE, $idCancelBtn
            ExitLoop

        Case $idOkBtn
            Local $ret = GetReferralInfo(GUICtrlRead($referralId), GUICtrlRead($accessCode))
            If $ret == True Then
                PopulateReferralInfo()
            EndIf
            ExitLoop
    EndSwitch
WEnd

; Delete the previous GUI and all controls.
GUIDelete($gui)

Func GetReferralInfo($id, $code)
    ; Display IE browser, log in web referral page.
    Local $oIE = _IECreate ("https://www.extendedcare.com/professional/WebReferral/Logon.aspx")
    Local $oForm = _IEFormGetObjByName ($oIE, "Logon")
    Local $oQuery1 = _IEFormElementGetObjByName ($oForm, "neReferral$Number")
    Local $oQuery2 = _IEFormElementGetObjByName ($oForm, "txtAccessCode")
    _IEFormElementSetValue ($oQuery1, $id)
    _IEFormElementSetValue ($oQuery2, $code)
    Local $oButton=_IEGetObjById($oIE, "btnViewReferral")
    _IEAction ($oButton, "click")
    _IELoadWait($oIE, 0)

    ; Check for any error.
    Local $oErrMsg = _IEGetObjById($oIE, "valSummary")
    Local $iCmp = StringCompare($oErrMsg, " ")
    If $iCmp <> 0 Then
        _IEQuit($oIE)
        MsgBox($MB_ICONERROR, "Sharp Referral", "Invalid Referral ID and/or Access Code!")
        ;Return False
    EndIf

	; Save referral info as pdf.
	Local $dtCur = _Date_Time_GetSystemTime()
	$fName = "sharp_referral_" & _Date_Time_SystemTimeToDateStr($dtCur)
	MsgBox($MB_OK, "debug", $fName)

	; Done, close browser.
	;Sleep(2000)
	_IEQuit($oIE)
    Return True
EndFunc

Func PopulateReferralInfo()
	; Execute application to populate referral info.
    Run("java -jar SharpReferral.jar " & $fName)
EndFunc

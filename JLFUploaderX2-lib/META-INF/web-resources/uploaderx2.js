/*
 - event types that will come in to handleFrameWorkMessage are:
 - "Request",  sType, oData    //this is 'messageHostedComponents'; requires response
 - "Messaging", oData          //generic messaging; does not expect a response
 */


// Command to open popover from commandline
// HSC.sendMessage("uploaderx2",{"sType":"STARTUPLOAD","appId":"","caseId":""})

var uploaderx2 = {



    hFiles:   {
        "resources/uploaderx2.html":    {sType:"html"},
        "resources/js/bootstrap.js":    {sType:"js"},
        "resources/js/bootstrap-filestyle.js":    {sType:"js"},
        "resources/js/handlebars-v3.0.3.js":    {sType:"js"},
        "resources/js/javalargefileuploader.js":    {sType:"js"},
        "resources/css/bootstrap.css":    {sType:"css"},
        "resources/css/bootstrap-theme.css":    {sType:"css"}
    },
    //construct object
    jlfu :null,

    currentApp:{
        appId:null,
        caseId:null,
        status:null,
        url:null
    },
    // backward compartible
    hMessageInterests : {}, //hashtable makes it easier to determine

    //-------------------------------- init -----------------------------
    //called by HSC after loading is complete

    init: function () {

        uploaderx2.jlfu=new JavaLargeFileUploader();
      //  $('#myModal').modal('show');
        $("#filesPanel").hide();
//            $("#showLoader").on("click",function(e){
//                $('#myModal').modal('show');
//            });


        //call the initializing function
        uploaderx2.jlfu.initialize(function (pendingFiles) {
            $("#resumeAll").hide();
            $("#pauseAll").hide();
            $("#cancelAll").hide();
            for (key in pendingFiles) {
                $("#resumeAll").show();
                $("#pauseAll").show();
                $("#cancelAll").show();
                $("#filesPanel").show();
                var pendingFile = pendingFiles[key];

                //create a control element for each of them
                uploaderx2.addx(pendingFile);

                //if the file is complete
                if (pendingFile.fileComplete) {
                    //specify it in the em element
                    uploaderx2.updateProgres("#" + pendingFile.id,Math.floor(100));
                    // alert("file: "+pendingFile.originalFileName +" is complete");
//                        document.getElementById(pendingFile.id).children[0].textContent = "The file '" + pendingFile.originalFileName + "' has been fully uploaded (" + pendingFile.fileCompletion + "/" + pendingFile.originalFileSize + ").";
//                        deleteElementsButEmAndCancel(pendingFile.id);
                    $("#stats_"+pendingFile.id).text("  Upload of " + pendingFile.originalFileName + " (" + pendingFile.originalFileSize + ") completed.");

                    uploaderx2.updateProgres("#" + pendingFile.id,Math.floor(100));

                    $("#pause_"+pendingFile.id).remove();
                    $("#resume_"+pendingFile.id).remove();
                    $("#cancel_"+pendingFile.id).remove();
                }
                //else if incomplete and pending
                else if (pendingFile.fileCompletion) {
                    //alert("The file '" + pendingFile.originalFileName + "' has been partially uploaded (" + pendingFile.fileCompletion + "/" + pendingFile.originalFileSize + "). Reselect it to continue uploading it or cancel it.");
                    //describe the file so that the user can select it again
//                        document.getElementById(pendingFile.id).children[0].textContent = "The file '" + pendingFile.originalFileName + "' has been partially uploaded (" + pendingFile.fileCompletion + "/" + pendingFile.originalFileSize + "). Reselect it to continue uploading it or cancel it.";
                    $("#pause_"+pendingFile.id).hide()
                    $("#resume_"+pendingFile.id).hide()
                    $("#cancel_"+pendingFile.id).show();
                    $("#stats_"+pendingFile.id).text("..")
                    $("#select_"+pendingFile.id).text("The file '" + pendingFile.originalFileName + "' has been partially uploaded (" + pendingFile.fileCompletion + "/" + pendingFile.originalFileSize + "). Reselect it to continue uploading it or cancel it.")

                    uploaderx2.updateProgres("#" + pendingFile.id,Math.floor(pendingFile.percentageCompleted));
                    //set progress in progress bar
//                        $("#" + pendingFile.id).children(".progressbar").progressbar({
//                            value: Math.floor(pendingFile.percentageCompleted)
//                        });

                    //set if there is a rate configured
                    if (pendingFile.rateInKiloBytes) {
                        alert("rate is" +pendingFile.rateInKiloBytes);
//                            document.getElementById(pendingFile.id).children[2].value = pendingFile.rateInKiloBytes;
                    }

                }

            }

        }, function (message) {
            alert(message);
        });

        //specify some configuration
        uploaderx2.jlfu.setMaxNumberOfConcurrentUploads(3);
        uploaderx2.jlfu.getErrorMessages()[9] = "More than 3 pending upload, file is queued.";



        $("#pauseAll").on("click", function (e){
            uploaderx2.jlfu.pauseAllFileUploads();
        })


        $("#resumeAll").on("click", function (e){
            uploaderx2.jlfu.resumeAllFileUploads();
        })

        $("#cancelAll").on("click", function (e){
            uploaderx2.jlfu.clearFileUpload(function(e) {window.location.reload();});
        })

        $('#fileSelection').on("change", function(e) {
            $("#filesPanel").show();

            //hide the fileupload and append a new one
            //  appendFileInputElement();

            //process the file upload
            uploaderx2.jlfu.fileUploadProcess(e.target,

                //define a start callback to create the UI that will interact with the file upload
                function (pendingFile, origin) {
                    uploaderx2.addx(pendingFile);
                    $("#resumeAll").show();
                    $("#pauseAll").show();
                    $("#cancelAll").show();
//                            appendUploadControls(pendingFile.id);
                },

                //define a progressCallback to show the progress in the em elements
                function (pendingFile, percentageCompleted, uploadRate, estimatedRemainingTime, origin) {
                    // $(pendingFile.id)
                    $("#pause_"+pendingFile.id).show()
                    $("#resume_"+pendingFile.id).show()
                    $("#cancel_"+pendingFile.id).show();

                    $("#select_"+pendingFile.id).text("");
                    // document.getElementById(pendingFile.id).children[0].textContent = "Uploading " + pendingFile.originalFileName + " ... (" + percentageCompleted + "% complete) at " + uploadRate + " per second. " + estimatedRemainingTime + " remaining.";
                    uploaderx2.updateProgres("#" + pendingFile.id,Math.floor(percentageCompleted));

//                            $("#" + pendingFile.id+" .progress-bar").children(".progress-bar").progressbar({
//                                value:
//                            });

                    $("#stats_"+pendingFile.id).text("(" + percentageCompleted + "% complete) at " + uploadRate + " per second. " + estimatedRemainingTime + " remaining.")
                },

                //define a finishCallback showing the completion in the em element
                function (pendingFile, origin) {
                    //    alert( "Upload of " + pendingFile.originalFileName + " (" + pendingFile.originalFileSize + ") completed.")
//                            document.getElementById(pendingFile.id).children[0].textContent = "Upload of " + pendingFile.originalFileName + " (" + pendingFile.originalFileSize + ") completed.";
//                            deleteElementsButEmAndCancel(pendingFile.id);

                    $("#stats_"+pendingFile.id).text("  Upload of " + pendingFile.originalFileName + " (" + pendingFile.originalFileSize + ") completed.");

                    uploaderx2.updateProgres("#" + pendingFile.id,Math.floor(100));

                    $("#pause_"+pendingFile.id).remove();
                    $("#resume_"+pendingFile.id).remove();
                    $("#cancel_"+pendingFile.id).remove();
                    uploaderx2. doMsg();
                },

                //define an exception callback
                function (message, origin, pendingFile /* pending file object that can be null! if null, it is a general error */ ) {
                    if (pendingFile) {
                        document.getElementById(pendingFile.id).children[0].textContent = message;
                    } else {
                        document.getElementById("error").textContent = message;
                    }
                });




        });

        HSC.registerMsgListener ("uploaderx2", true); //listen for messages


        HSC.setHook("navigate",function (PsNavPointId, PoNavData) {


        });
    },
    updateProgres:function (pref,v){
        $(pref+' '+'.progress-bar').css('width', v+'%').attr('aria-valuenow', v);
        $(pref+' '+'.progress-bar').text(" "+v +"%");
    },

    updateProgress:function (xFile){
        var  pref= xFile
        var  v= xFile

        $(pref+' '+'.progress-bar').css('width', v+'%').attr('aria-valuenow', v);
        $(pref+' '+'.progress-bar').text(" "+v +"%");
    },

    alertx:function (s,m) {
        $("#info_"+s).text(m);
    },


    addx:function (xFile) {
        var context  = {xfileName: xFile.originalFileName, "xid": xFile.id}
        var template = Handlebars.compile($("#file-item").html());
        var html     = template(context);

        $("#fileList").append(html);
        $("#pause_"+xFile.id).on("click", function (e){

            uploaderx2.jlfu.pauseFileUpload(xFile.id, function (pendingFile) {
                $("#pause_"+xFile.id).prop('disabled', "disabled");
                $("#resume_"+xFile.id).removeProp('disabled');
                alertx(xFile.id,"paused")
                //                    alert(pendingFile.originalFileName + " is paused.");
            });
        })

        $("#resume_"+xFile.id).on("click", function (e){
            uploaderx2.jlfu.resumeFileUpload(xFile.id, function (pendingFile) {
                $("#pause_"+xFile.id).removeProp('disabled');
                $("#resume_"+xFile.id).prop('disabled', "disabled");
                alertx(xFile.id,"")
                //                    alert(pendingFile.originalFileName + " is paused.");
            });
        })


        $("#cancel_"+xFile.id).on("click", function (e){
            uploaderx2.jlfu.cancelFileUpload(xFile.id, function (pendingFileId) {
                $("#xrow_"+xFile.id).remove();
            });
        })

    },

    //-------------------------------- visibilityChanged ----------------
    //notification from container

    visibilityChanged: function (PbNewVisibility) {
//       _log());
        HSC.log ("uploaderx2 is now " + (PbNewVisibility ? "visible" : "invisible"));
    },

    //-------------------------------- handleFrameWorkMessage -----------
    //request from container; requires a return object

    handleFrameWorkMessage: function (PsEventType, PoData) {

     //   alert(">>>PsEventType="+PsEventType +"PoData"+PoData);
        if (PsEventType == "Messaging") {
            if(PoData ) {
                var sType = PoData.sType;
                if (sType == "STARTUPLOAD") {
                    /*            document.getElementById ("SCX_Field").value = oReqData;*/


                    // TODO validate


                        if(PoData.hasOwnProperty("appId") && PoData.hasOwnProperty("caseId") ){

                            var appx={};
                                appx.appId=PoData.appId
                                appx.caseId=PoData.caseId
                                appx.status="NOT_STARTED"
                                appx.url=null;
                            uploaderx2.currentApp= appx;
                            $('#myModal').modal('show');
                        }else {
                            alert(PoData+"PoData does not have requred properties : appId and/or caseid");
                        }
                    }
                    return null;
            }
            else if (sType == "CHECKUOPLOAD") {

                return null;
            }
            else {
                alert ("Unexpected Request type " + sReqType + " to SCX");
                return null;
            }


        }
        else {
            alert ("Unexpected handleEvent type " + PsEventType + " to SCX");
            return null;
        }

    },

    //-------------------------------- doPushToCurrentApp ---------------
    //called from button click

    doPushToCurrentApp: function () {
        // M2 stuff
        //HSC.pushToCurrentApp ("SC Changed", document.getElementById ("SCX_Field").value);
    },

    //-------------------------------- doMsg ----------------------------
    //called from button click

    doMsg: function () {
        //var sOutMsgTxt = document.getElementById ("SCX_OutMsg").value;
        var oMsg       = {sType:"UPLOADSTATUS", sData:uploaderx2.currentApp};



        HSC.sendMessage (uploaderx2.currentApp.appId, oMsg, "all windows");
    },

    _log:function(m) {
        // I dont care about IE at the moment.
        console.log(m);

        HSC.log (m);
    }


};


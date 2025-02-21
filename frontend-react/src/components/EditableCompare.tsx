import {
    forwardRef,
    ReactElement,
    Ref,
    useCallback,
    useEffect,
    useImperativeHandle,
    useRef,
    useState,
} from "react";
import DOMPurify from "dompurify";
import { ScrollSync, ScrollSyncPane } from "react-scroll-sync";

import { textDifferMarkup } from "../utils/DiffCompare/TextDiffer";
import { jsonDifferMarkup } from "../utils/DiffCompare/JsonDiffer";
import { checkJson, splitOn } from "../utils/misc";

import { showError } from "./AlertNotifications";

// interface on Component that is callable
export type EditableCompareRef = {
    getEditedText: () => string;
    getOriginalText: () => string;
    refreshEditedText: (updatedjson?: string) => boolean; // returns true if valid JSON
    isValidSyntax: () => boolean;
};

interface EditableCompareProps {
    original: string;
    modified: string;
    jsonDiffMode: boolean; // true is json aware compare, false is a text compare
    onChange: (text: string, isValid: boolean) => void;
}

/**
 *
 * Warning: DO NOT assign a `key` to the div inside `<ScrollSyncPane>` it will break it.
 *
 * NOTES:
 *
 * - The diff is written for unstructured text, it's probably better to replace with a
 *      json differ and pass json into the function instead of text. Rewrite to understand json!
 *
 * - Doing highlights in js is a well defined problem. But go check out this AMAZING codepen
 *      https://codepen.io/lonekorean/pen/gaLEMR  (make sure to click on the Toogle Prespective button)
 *
 * **/

export const EditableCompare = forwardRef(
    // allows for functions on components (useImperativeHandle)
    (
        { jsonDiffMode, modified, onChange, original }: EditableCompareProps,
        ref: Ref<EditableCompareRef>,
    ): ReactElement => {
        // useRefs are used to access html elements directly (instead of document.getElementById)
        const staticDiffRef = useRef<HTMLDivElement>(null);
        const editDiffRef = useRef<HTMLTextAreaElement>(null);
        const editDiffBackgroundRef = useRef<HTMLDivElement>(null);

        const [textAreaContent, setTextAreaContent] = useState("");

        const [leftHandSideHighlightHtml, setLeftHandSideHighlightHtml] =
            useState("");
        const [rightHandSideHighlightHtml, setRightHandSideHighlightHtml] =
            useState("");

        // the API call into this forwardRef well say if json is valid (to enable save button)
        const [isValidSyntax, setIsValidSyntax] = useState(true);

        const refreshDiffCallback = useCallback(
            (originalText: string, modifiedText: string) => {
                if (originalText.length === 0 || modifiedText.length === 0) {
                    return;
                }

                // jsonDiffMode requires json be valid. If it's not then don't run it.
                const { valid, offset } = checkJson(modifiedText);
                if (!valid) {
                    // clear the diff on the left since the right is invalid
                    setLeftHandSideHighlightHtml(originalText);

                    // show where the error is:
                    const start = Math.max(offset - 4, 0); // don't let go negative
                    const end = Math.min(offset + 4, modifiedText.length); // don't let go past len

                    const threeParts = splitOn(modifiedText, start, end);
                    // we're using HTML5's <s> tag to show error, style sets background to red.
                    const errorHtml = `${threeParts[0]}<s data-testid="EditableCompare__errorDiff">${threeParts[1]}</s>${threeParts[2]}`;
                    setRightHandSideHighlightHtml(errorHtml);

                    return;
                }

                const result = jsonDiffMode
                    ? jsonDifferMarkup(
                          JSON.parse(originalText),
                          JSON.parse(modifiedText),
                      )
                    : textDifferMarkup(originalText, modifiedText);

                // now stick it back into the edit boxes.
                if (result.left.markupText !== leftHandSideHighlightHtml) {
                    setLeftHandSideHighlightHtml(result.left.markupText);
                }

                // we only change the highlighting on the BACKGROUND div so we don't mess up typing/cursor
                if (result.right.markupText !== rightHandSideHighlightHtml) {
                    setRightHandSideHighlightHtml(result.right.markupText);
                }
            },
            [
                leftHandSideHighlightHtml,
                rightHandSideHighlightHtml,
                jsonDiffMode,
            ],
        );

        // force an update of the content
        // will update with the provided content or infer from previous content
        const refreshEditedText = useCallback(
            (updatedJson: string | undefined) => {
                let formattedText = updatedJson || textAreaContent;
                const { valid, errorMsg } = checkJson(formattedText);

                if (valid) {
                    formattedText = JSON.stringify(
                        JSON.parse(formattedText),
                        null,
                        2,
                    );
                    setTextAreaContent(formattedText);
                } else {
                    showError(`JSON data generated an error "${errorMsg}"`);
                }

                setIsValidSyntax(valid);
                refreshDiffCallback(original, formattedText);

                return valid;
            },
            [original, textAreaContent, refreshDiffCallback],
        );

        const onChangeHandler = useCallback(
            (newText: string) => {
                setTextAreaContent(newText);
                // Disable highlighting while the user's typing
                setRightHandSideHighlightHtml("");
                onChange(newText, checkJson(newText).valid);
            },
            [setTextAreaContent, setRightHandSideHighlightHtml, onChange],
        );

        useImperativeHandle(
            ref,
            () => ({
                getEditedText() {
                    return textAreaContent;
                },
                getOriginalText() {
                    return original;
                },
                refreshEditedText,
                isValidSyntax() {
                    return isValidSyntax;
                },
            }),
            [textAreaContent, original, isValidSyntax, refreshEditedText],
        );

        useEffect(() => {
            if (modified?.length > 0 && textAreaContent.length === 0) {
                // initialization only
                onChangeHandler(modified);
            }
        }, [textAreaContent, modified, onChangeHandler]);

        return (
            <ScrollSync>
                <div className="rs-editable-compare-container differ-marks">
                    <div className="rs-editable-stacked-container">
                        <ScrollSyncPane>
                            <div
                                ref={staticDiffRef}
                                className="rs-editable-compare-base rs-editable-compare-static"
                                contentEditable={false}
                                dangerouslySetInnerHTML={{
                                    __html: DOMPurify.sanitize(original),
                                }}
                            />
                        </ScrollSyncPane>

                        <ScrollSyncPane>
                            <div
                                ref={editDiffBackgroundRef}
                                className="rs-editable-compare-base rs-editable-compare-background"
                                dangerouslySetInnerHTML={{
                                    __html: DOMPurify.sanitize(
                                        leftHandSideHighlightHtml,
                                    ),
                                }}
                            />
                        </ScrollSyncPane>
                    </div>
                    <div className="rs-editable-stacked-container">
                        <ScrollSyncPane>
                            <textarea
                                className="rs-editable-compare-base rs-editable-compare-edit"
                                ref={editDiffRef}
                                value={textAreaContent}
                                onChange={(e) => {
                                    onChangeHandler(e?.target?.value || "");
                                }}
                                spellCheck="false"
                                data-testid="EditableCompare__textarea"
                            />
                        </ScrollSyncPane>

                        <ScrollSyncPane>
                            <div
                                ref={editDiffBackgroundRef}
                                className="rs-editable-compare-base rs-editable-compare-background"
                                dangerouslySetInnerHTML={{
                                    // the extra `<br/>` is required for some odd reason.
                                    // It's in the shadowdom of the textarea.
                                    __html: `${DOMPurify.sanitize(
                                        rightHandSideHighlightHtml,
                                    )}<br/>`,
                                }}
                            />
                        </ScrollSyncPane>
                    </div>
                </div>
            </ScrollSync>
        );
    },
);

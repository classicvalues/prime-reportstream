import { screen } from "@testing-library/react";

import content from "../../content/content.json";
import { renderWithBase } from "../../utils/CustomRenderUtils";

import Hero from "./Hero";

describe("Hero rendering", () => {
    beforeEach(() => {
        renderWithBase(<Hero />);
    });

    test("Title and Summary render on Hero", () => {
        const title = screen.getByTestId("heading");
        const summary = screen.getByTestId("summary");

        expect(title.innerHTML).toEqual(content.title);
        expect(summary.innerHTML).toEqual(content.summary);
    });
});

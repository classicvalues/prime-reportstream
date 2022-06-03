import { fireEvent, render, screen, within } from "@testing-library/react";

import Pagination, {
    PaginationProps,
    SlotItem,
    OVERFLOW_INDICATOR,
} from "./Pagination";

describe("Pagination", () => {
    // Text match for getting pagination elements by role to capture previous
    // and next links, page numbers, and overflow indicators (which are for
    // presentation).
    const ITEM_ROLE = /listitem|presentation/i;

    test.each([
        {
            description: "on the first page of an unbounded set",
            slots: [1, 2, 3, 4, 5, 6, OVERFLOW_INDICATOR],
            currentPageNum: 1,
            expectedItems: ["1", "2", "3", "4", "5", "6", "…", "Next"],
        },
        {
            description: "on the second page of an unbounded set",
            slots: [1, 2, 3, 4, 5, 6, OVERFLOW_INDICATOR],
            currentPageNum: 2,
            expectedItems: [
                "Previous",
                "1",
                "2",
                "3",
                "4",
                "5",
                "6",
                "…",
                "Next",
            ],
        },
        {
            description: "on the first page of a bounded set",
            slots: [1, 2],
            currentPageNum: 1,
            expectedItems: ["1", "2", "Next"],
        },
        {
            description: "on the last page of a bounded set",
            slots: [1, 2],
            currentPageNum: 2,
            expectedItems: ["Previous", "1", "2"],
        },
        {
            description: "when there is only one page",
            slots: [1],
            currentPageNum: 1,
            expectedItems: ["1"],
        },
    ])(
        "Handles Previous and Next links $description",
        ({ slots, currentPageNum, expectedItems }) => {
            const props: PaginationProps = {
                slots: slots as SlotItem[],
                currentPageNum,
                setSelectedPage: jest.fn(),
            };
            render(<Pagination {...props} />);

            const list = screen.getByRole("list");
            const { getAllByRole } = within(list);
            const items = getAllByRole(ITEM_ROLE);
            const itemContents = items.map((item) => item.textContent);
            expect(itemContents).toStrictEqual(expectedItems);
        }
    );

    test("Clicking on pagination items invokes the setSelectedPage callback", () => {
        const mockSetSelectedPage = jest.fn();
        const props: PaginationProps = {
            slots: [1, 2, 3],
            currentPageNum: 2,
            setSelectedPage: mockSetSelectedPage,
        };
        render(<Pagination {...props} />);

        fireEvent.click(screen.getByText("Previous"));
        expect(mockSetSelectedPage).toHaveBeenLastCalledWith(1);

        fireEvent.click(screen.getByText("1"));
        expect(mockSetSelectedPage).toHaveBeenLastCalledWith(1);

        fireEvent.click(screen.getByText("2"));
        expect(mockSetSelectedPage).toHaveBeenLastCalledWith(2);

        fireEvent.click(screen.getByText("3"));
        expect(mockSetSelectedPage).toHaveBeenLastCalledWith(3);

        fireEvent.click(screen.getByText("Next"));
        expect(mockSetSelectedPage).toHaveBeenLastCalledWith(3);
    });

    test("Renders the expected markup when the current page is between two other pages", () => {
        const props: PaginationProps = {
            slots: [1, OVERFLOW_INDICATOR, 6, 7, 8, 9, OVERFLOW_INDICATOR],
            currentPageNum: 7,
            setSelectedPage: jest.fn(),
        };
        const { asFragment } = render(<Pagination {...props} />);
        expect(asFragment()).toMatchSnapshot();
    });

    test("Renders the expected markup when the current page is the last page", () => {
        const props: PaginationProps = {
            slots: [1, 2],
            currentPageNum: 2,
            setSelectedPage: jest.fn(),
        };
        const { asFragment } = render(<Pagination {...props} />);
        expect(asFragment()).toMatchSnapshot();
    });
});
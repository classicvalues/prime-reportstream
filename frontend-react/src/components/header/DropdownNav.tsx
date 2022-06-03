import React, { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import { Menu, NavDropDownButton } from "@trussworks/react-uswds";

import { MarkdownDirectory } from "../Markdown/MarkdownDirectory";
import { CheckFeatureFlag } from "../../pages/misc/FeatureFlags";

type NonStaticOption = Omit<MarkdownDirectory, "files">;
interface DropdownNavProps {
    label: string;
    root: string;
    directories: MarkdownDirectory[] | NonStaticOption[];
}

export const makeNonStaticOption = (
    title: string,
    slug: string
): NonStaticOption => {
    return { title, slug };
};

const DropdownNav = ({ label, root, directories }: DropdownNavProps) => {
    const [isOpen, setIsOpen] = useState(false);
    /* Used since setIsOpen cannot be directly called in useEffect */
    const handleClick = () => setIsOpen(false);
    /* This has to be down on "mouseup" not "mousedown" otherwise clicking
       any link in the list will result in the menu closing without registering
       the click on the link; thus, you're not directed to the page desired */
    useEffect(() => {
        document.body.addEventListener("mouseup", handleClick);
        return () => {
            document.body.removeEventListener("mouseup", handleClick);
        };
    }, []);
    const navMenu = directories.map((dir) => (
        <NavLink to={`${root}/${dir.slug}`}>{dir.title}</NavLink>
    ));
    return (
        <>
            <NavDropDownButton
                menuId={root}
                onToggle={(): void => {
                    setIsOpen(!isOpen);
                }}
                isOpen={isOpen}
                label={label}
                isCurrent={isOpen}
            />
            <Menu
                items={navMenu}
                isOpen={isOpen}
                id={root}
                onClick={(): void => setIsOpen(false)}
            />
        </>
    );
};

export const AdminDropdown = () => {
    const pages = [
        makeNonStaticOption("Organization Settings", "settings"),
        makeNonStaticOption("Feature Flags", "features"),
        makeNonStaticOption("Guides", "guides/create-markdown-pages"),
    ];
    if (CheckFeatureFlag("value-sets"))
        pages.push(makeNonStaticOption("Value Sets", "value-sets"));
    return <DropdownNav label={"Admin"} root={"/admin"} directories={pages} />;
};

export const GettingStartedDropdown = () => {
    return (
        <DropdownNav
            label="Getting Started"
            root="/getting-started"
            directories={[
                makeNonStaticOption(
                    "Public health departments",
                    "public-health-departments/overview"
                ),
                makeNonStaticOption(
                    "Testing facilities",
                    "testing-facilities/overview"
                ),
            ]}
        />
    );
};

export const HowItWorksDropdown = () => {
    return (
        <DropdownNav
            label="How It Works"
            root="/how-it-works"
            directories={[
                makeNonStaticOption("About", "about"),
                makeNonStaticOption("Where we're live", "where-were-live"),
                makeNonStaticOption(
                    "System and settings",
                    "system-and-settings"
                ),
                makeNonStaticOption("Security practices", "security-practices"),
            ]}
        />
    );
};

export default DropdownNav;
import ReportLink from "../Table/ReportLink";
import { useOrgName } from "../../../hooks/UseOrgName";
import { RSDelivery } from "../../../config/endpoints/deliveries";
import Crumbs, { CrumbsProps } from "../../../components/Crumbs";
import Title from "../../../components/Title";

interface Props {
    /* REQUIRED
    Passing in a report allows this component to extract key properties (id) 
    and display them on the DeliveryDetail page. */
    report: RSDelivery | undefined;
}

function Summary(props: Props) {
    const { report }: Props = props;
    const orgName: string = useOrgName();
    const crumbProps: CrumbsProps = {
        crumbList: [
            { label: "Daily Data", path: "/daily-data" },
            { label: "Details" },
        ],
        noPadding: true,
    };

    return (
        <div className="grid-container grid-row tablet:margin-top-6">
            <div className="grid-col-fill">
                <Crumbs {...crumbProps} />
                <Title preTitle={orgName} title={report?.reportId || ""} />
            </div>
            <div className="grid-col-auto margin-bottom-5 margin-top-auto">
                <ReportLink report={report} button />
            </div>
        </div>
    );
}

export default Summary;
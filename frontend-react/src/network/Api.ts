import axios, { AxiosInstance } from "axios";

import {
    getStoredOktaToken,
    getStoredOrg,
} from "../components/GlobalContextProvider";

import { Endpoint } from "./NetworkHooks";

export abstract class Api {
    /*
        Keeping these out of component code, as well, keeps things
        pretty clean. Happy devs, happy Kev!
    */
    static accessToken: string = getStoredOktaToken();
    static organization: string = getStoredOrg();
    static baseUrl: string = "/api";
    static options = {
        headers: {
            Authorization: `Bearer ${this.accessToken}`,
            Organization: this.organization,
        },
    };

    /*
        The general idea is an instance per API since headers may vary.
        This is a default that can be overridden 
    */
    static instance: AxiosInstance = axios.create({
        baseURL: `${process.env.REACT_APP_BACKEND_URL}`,
        headers: {
            Authorization: `Bearer ${this.accessToken}`,
            Organization: this.organization,
        },
        responseType: "json",
    });

    /*
        Super convenient way to generate an Endpoint in child classes!
        ChildClass.generateEndpoint(this.baseUrl, this)
    */
    static generateEndpoint(urlParam: string, api: typeof Api): Endpoint {
        return {
            url: urlParam,
            api: api,
        };
    }
}

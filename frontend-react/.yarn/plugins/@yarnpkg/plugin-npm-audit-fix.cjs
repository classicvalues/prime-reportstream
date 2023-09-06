/* eslint-disable */
//prettier-ignore
module.exports = {
name: "@yarnpkg/plugin-npm-audit-fix",
factory: function (require) {
var plugin=(()=>{var k=Object.create;var m=Object.defineProperty;var I=Object.getOwnPropertyDescriptor;var P=Object.getOwnPropertyNames;var S=Object.getPrototypeOf,L=Object.prototype.hasOwnProperty;var v=(r=>typeof require<"u"?require:typeof Proxy<"u"?new Proxy(r,{get:(c,e)=>(typeof require<"u"?require:c)[e]}):r)(function(r){if(typeof require<"u")return require.apply(this,arguments);throw new Error('Dynamic require of "'+r+'" is not supported')});var M=(r,c)=>{for(var e in c)m(r,e,{get:c[e],enumerable:!0})},x=(r,c,e,s)=>{if(c&&typeof c=="object"||typeof c=="function")for(let i of P(c))!L.call(r,i)&&i!==e&&m(r,i,{get:()=>c[i],enumerable:!(s=I(c,i))||s.enumerable});return r};var N=(r,c,e)=>(e=r!=null?k(S(r)):{},x(c||!r||!r.__esModule?m(e,"default",{value:r,enumerable:!0}):e,r)),U=r=>x(m({},"__esModule",{value:!0}),r);var T={};M(T,{Environment:()=>y,Severity:()=>w,default:()=>j});var D=v("stream"),A=v("@yarnpkg/cli"),t=v("@yarnpkg/core"),l=v("clipanion"),n=N(v("typanion")),g=class extends A.BaseCommand{constructor(){super(...arguments);this.all=l.Option.Boolean("-A,--all",!1,{description:"Audit dependencies from all workspaces"});this.recursive=l.Option.Boolean("-R,--recursive",!1,{description:"Audit transitive dependencies as well"});this.environment=l.Option.String("--environment",y.All,{description:"Which environments to cover",validator:n.isEnum(y)});this.severity=l.Option.String("--severity",w.Info,{description:"Minimal severity requested for packages to be displayed",validator:n.isEnum(w)});this.excludes=l.Option.Array("--exclude",[],{description:"Array of glob patterns of packages to exclude from audit"});this.ignores=l.Option.Array("--ignore",[],{description:"Array of glob patterns of advisory ID's to ignore in the audit report"});this.mode=l.Option.String("--mode",{description:"Change what artifacts installs generate",validator:n.isEnum(t.InstallMode)})}async execute(){let e=await this.initState(),{configuration:s,project:i,cache:o}=e;return(await t.StreamReport.start({configuration:s,stdout:this.context.stdout},async a=>{let u=await a.startTimerPromise("Audit step",()=>this.getAdvisories());for(let p of u){let f=t.structUtils.prettyIdent(s,t.structUtils.parseIdent(p.module_name)),b=t.structUtils.prettyRange(s,p.vulnerable_versions),h=t.structUtils.prettyRange(s,p.patched_versions);await a.startTimerPromise(`Advisory for ${f} at ${b}, patched at ${h}`,()=>this.handleAdvisory(a,e,p))}await i.install({report:a,cache:o,mode:this.mode})})).exitCode()}async handleAdvisory(e,s,i){let{configuration:o,resolver:d,project:a}=s,u=t.structUtils.parseIdent(i.module_name);for(let p of a.storedDescriptors.values()){if(!t.structUtils.areIdentsEqual(p,u)||t.structUtils.isVirtualDescriptor(p))continue;let f=a.storedPackages.get(a.storedResolutions.get(p.descriptorHash));if(!f||!t.semverUtils.satisfiesWithPrereleases(f.version,i.vulnerable_versions))continue;e.reportInfo(t.MessageName.UNNAMED,`Found vulnerable ${t.structUtils.prettyLocator(o,f)} (via ${t.structUtils.prettyRange(o,p.range)})`);let h=(await d.getCandidates(p,new Map,{project:a,report:e,resolver:d}))[0];if(!h){e.reportError(t.MessageName.UNNAMED,`No candidates found for ${t.structUtils.prettyDescriptor(o,p)}`);continue}let R=await d.resolve(h,{project:a,report:e,resolver:d});if(!t.semverUtils.satisfiesWithPrereleases(R.version,i.patched_versions)){e.reportWarning(t.MessageName.UNNAMED,`No compatible patched version found for ${t.structUtils.prettyDescriptor(o,p)}`);continue}e.reportInfo(t.MessageName.UNNAMED,`Setting resolution for ${t.structUtils.prettyDescriptor(o,p)} to ${t.structUtils.prettyLocator(o,h)}`),this.setResolution(s,p,R)}}setResolution(e,s,i){let{project:o}=e,d=t.structUtils.convertLocatorToDescriptor(i);o.storedDescriptors.set(s.descriptorHash,s),o.storedDescriptors.set(d.descriptorHash,d),o.resolutionAliases.set(s.descriptorHash,d.descriptorHash)}async initState(){let e=await t.Configuration.find(this.context.cwd,this.context.plugins),{project:s,workspace:i}=await t.Project.find(e,this.context.cwd),o=await t.Cache.find(e);if(!i)throw new A.WorkspaceRequiredError(s.cwd,this.context.cwd);await s.restoreInstallState();let d=e.makeResolver();return{configuration:e,workspace:i,cache:o,project:s,resolver:d}}async getAdvisories(){let e=new D.PassThrough,s=[];e.on("data",a=>{s.push(a)});let i=["npm","audit","-AR","--json"];this.all&&i.push("--all"),this.recursive&&i.push("--recursive"),this.environment&&i.push("--environment",this.environment),this.severity&&i.push("--severity",this.severity);for(let a of this.excludes)i.push("--exclude",a);for(let a of this.ignores)i.push("--ignore",a);await this.cli.run(i,{stdout:e}),e.end();let o=JSON.parse(Buffer.concat(s).toString());if(!_(o))throw new Error("Unexpected yarn npm audit result");let d=[];for(let[a,u]of Object.entries(o.advisories))d.push(u);return d}};g.paths=[["npm","audit","fix"]],g.usage=l.Command.Usage({description:"Attempt to fix advisories reported by the audit",details:`
    This command attempts to resolve security advisories on the packages you use
    by upgrading packages to patched versions if possible while respecting the 
    requested version ranges.

    Most flags do the same as their counterparts in \`yarn npm audit\`.

    The \`--mode\` flag does the same as its counterpart in \`yarn install\`.
    `,examples:[["Attempt to resolve all audit advisories","$0 npm audit -AR"]]});var _=n.isObject({advisories:n.isDict(n.isObject({module_name:n.isString(),vulnerable_versions:n.isString(),patched_versions:n.isString()},{extra:n.isDict(n.isUnknown())}))},{extra:n.isDict(n.isUnknown())}),y=(s=>(s.All="all",s.Production="production",s.Development="development",s))(y||{}),w=(o=>(o.Info="info",o.Low="low",o.Moderate="moderate",o.High="high",o.Critical="critical",o))(w||{}),$={commands:[g]},j=$;return U(T);})();
return plugin;
}
};

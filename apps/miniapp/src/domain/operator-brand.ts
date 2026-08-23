const LOGOS:Record<string,string>={
  GRAMEENPHONE:'/static/operators/grameenphone.svg',
  ROBI:'/static/operators/robi.svg',
  BANGLALINK:'/static/operators/banglalink.svg',
  AIRTEL:'/static/operators/airtel.svg',
  TELETALK:'/static/operators/teletalk.svg',
}

export const operatorLogo=(operatorCode:string)=>LOGOS[operatorCode]||''

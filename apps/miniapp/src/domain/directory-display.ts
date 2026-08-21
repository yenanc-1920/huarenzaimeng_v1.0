const CATEGORY_LABELS:Record<string,string>={
  LIFE_SERVICE:'生活服务',
  MEDICAL:'医疗服务',
}

export const directoryCategoryLabel=(category:string):string=>CATEGORY_LABELS[category]||'其他服务'

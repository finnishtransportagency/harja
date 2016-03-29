(ns harja.ui.dom-test
  (:require [cljs.test :as test :refer-macros [deftest is]]
            [harja.ui.dom :as dom]))

(deftest maarita-ie-versio-user-agentista
  (is (= (dom/maarita-ie-versio-user-agentista
           "Mozilla/5.0 (compatible; MSIE 8.0; Windows NT 5.1; Trident/4.0; InfoPath.2; SLCC1; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729; .NET CLR 2.0.50727)")
         8))
  (is (= (dom/maarita-ie-versio-user-agentista
           "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Win64; x64; Trident/5.0; .NET CLR 3.5.30729; .NET CLR 3.0.30729; .NET CLR 2.0.50727; Media Center PC 6.0)")
         9))
  (is (= (dom/maarita-ie-versio-user-agentista
           "Mozilla/5.0 (compatible; MSIE 10.0; Windows NT 7.0; InfoPath.3; .NET CLR 3.1.40767; Trident/6.0; en-IN)")
         10))
  (is (= (dom/maarita-ie-versio-user-agentista
           "Mozilla/1.22 (compatible; MSIE 10.0; Windows 3.1)")
         10))
  (is (= (dom/maarita-ie-versio-user-agentista
           "Mozilla/5.0 (compatible, MSIE 11, Windows NT 6.3; Trident/7.0; rv:11.0) like Gecko")
         11)))

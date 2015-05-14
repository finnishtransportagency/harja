(ns harja.palvelin.integraatiot.sampo.kuittaukset_test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.palvelin.integraatiot.sampo :refer [->Sampo] :as sampo]
            [harja.palvelin.integraatiot.sampo.maksuera :as maksuera]
            [hiccup.core :refer [html]]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [com.stuartsierra.component :as component]
            [harja.testi :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.jms :refer [feikki-sonja]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [clojure.core.async :refer [<! >! go] :as async]
            [harja.xml :as xml])
  (:import (java.io ByteArrayInputStream)
           (java.text SimpleDateFormat)))
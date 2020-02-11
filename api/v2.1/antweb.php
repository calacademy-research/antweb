<?php

  class antweb {

    protected $_db;

    public function __construct(PDO $db) {
      $this->_db = $db;

      // a list of valid arguments to check any incoming GETs against
      $this->validArguments = array(
        'occurrenceId', //occurrenceId
        'catalogNumber',
        'subfamily',
        'genus',
        'species', //specificEpithet
        'type', // typeStatus
        'bbox',
        'min_date', //dateIdentified
        'max_date',  //dateIdentified
        'min_elevation', //minimumElevationInMeters
        'max_elevation',  //minimumElevationInMeters
        'country',
        'state_province', //stateProvince
        'habitat',
        'georeferenced',
        'limit',
        'offset',
        'distinct',
        'rank',
        'fossil',
        'taxon_status',
        'date_collected_min',
        'date_collected_max',
        'collectioncode'
      );

      //a list of valid ranks to query on
      $this->valid_ranks = array(
        'species',
        'genus',
        'subfamily',
      );

      //list of valid characters
      $this->valid_chars = array('-','_',',','.',':','(',')',' ');

    }

    public function getColumnNames($table) {

      $sql = $this->_db->prepare("DESCRIBE " . $table);
      $sql->execute();
      if($sql->rowCount() > 0) {
        $columns = $sql->fetchAll(PDO::FETCH_ASSOC);
        $fields = array();
        foreach($columns AS $c) {
          $fields[] = $c['Field'];
        }
      }

      return $fields;

    }

    public function validCharacters($args) {

      //validate args for allowed characters
      foreach($args AS &$arg) {
        if(!ctype_alnum(str_replace($this->valid_chars,'',$arg))) {
          $arg = 'invalid';
        }
      }

      return $args;

    }

    //some of the characters coming out of the db are not utf8 encoded and throwing warnings in the log
    public function utf8Scrub($array) {

      array_walk_recursive(
              $array, function (&$value) {
                  $value = trim(preg_replace('/ +/', ' ', preg_replace('/[^A-Za-z0-9_.\/,:?=-]()/',' ', urldecode(html_entity_decode(strip_tags($value))))));
                  $value = htmlspecialchars(html_entity_decode($value, ENT_QUOTES, 'UTF-8'), ENT_QUOTES, 'UTF-8');
              }
      );

      return $array;

    }

    //for the sake of simplicity, the argument names are not necessarily the same as their corresponding field names
    //some arguments also need to be massaged into correct formats
    public function prepareArguments($args) {

      if(isset($args['type'])) {
        $args['typeStatus'] = "%" . $args['type'] . "%";
        unset($args['type']);
      }

      if(isset($args['collectioncode'])) {
        $args['fieldNumber'] = "%" . $args['collectioncode'] . "%";
        unset($args['collectioncode']);
      }

      if(isset($args['species'])) {
        $args['specificEpithet'] = $args['species'];
        unset($args['species']);
      }

      if(isset($args['state_province'])) {
        $args['stateProvince'] = $args['state_province'];
        unset($args['state_province']);
      }

      if(isset($args['habitat'])) {
        $args['habitat'] = "%" . $args['habitat'] . "%";
      }

      if(isset($args['bbox'])) {
        $coords = explode(',', $args['bbox']);
        if(count($coords) == 4) {
          $x['lat1'] = $coords[0];
          $y['lon1'] = $coords[1];
          $x['lat2'] = $coords[2];
          $y['lon2'] = $coords[3];

          asort($x);
          asort($y);

          $args['xcoord'] = $x;
          $args['ycoord'] = $y;

        }

        unset($args['bbox']);
      }

      if(isset($args['georeferenced'])) {
        $georeferenced = $args['georeferenced'];
        if(!is_null($georeferenced)) {
          $limits['georeferenced'] = 1;
        }
        unset($args['georeferenced']);
      }

      if(isset($args['limit']) ) {
        $limits['limit'] = $args['limit'];
        unset($args['limit']);
      }

      if(isset($args['offset'])) {
        $limits['offset'] = $args['offset'];
        unset($args['offset']);
      }

      $sql_const['args'] = $args;
      if(isset($limits)) $sql_const['limits'] = $limits;

      return $sql_const;

    }

    public function constructParams($params) {

      $args = $params['args'];
      if(isset($params['limits'])) {
        $limits = $params['limits'];
      }

      $sql = '';
      foreach($args AS $key => $val) {
        if(is_array($val)) {
          foreach($val AS $k => $v) {
            $params[$k] = $v;
          }
        }
        else {
          $params[$key] = $val;
        }
      }

      foreach($args AS $key => $val) {

        if($key == 'min_date') {
          $mind = 'dateIdentified';
          $sql .= sprintf(' AND `%s` >= :%s',$mind,$key);
        }
        elseif($key == 'max_date') {
          $maxd = 'dateIdentified';
          $sql .= sprintf(' AND `%s` <= :%s',$maxd,$key);
        }
        elseif($key == 'date_collected_min') {
          $dcmin = 'datecollected';
          $sql .= sprintf(' AND `%s` >= :%s',$dcmin,$key);
        }
        elseif($key == 'date_collected_max') {
          $dcmax = 'datecollected';
          $sql .= sprintf(' AND `%s` <= :%s',$dcmax,$key);
        }
        elseif($key == 'min_elevation') {
          $mine = 'minimumElevationInMeters';
          $sql .= sprintf(' AND `%s` >= :%s',$mine,$key);
        }
        elseif($key == 'max_elevation') {
          $maxe = 'minimumElevationInMeters';
          $sql .= sprintf(' AND `%s` <= :%s',$maxe,$key);
        }
        elseif($key == 'xcoord') {
          $sql .= ' AND decimalLatitude BETWEEN';
          $i = 0;
          foreach($val AS $coord => $val) {
            if($i == 0) {
              $sql .= sprintf(' :%s',$coord);
              $i++;
            }
            else {
              $sql .= sprintf(' AND :%s',$coord);
              $i = 0;
            }
          }
        }
        elseif($key == 'ycoord') {
          $sql .= ' AND decimalLongitude BETWEEN';
          $i = 0;
          foreach($val AS $coord => $val) {
            if($i == 0) {
              $sql .= sprintf(' :%s',$coord);
              $i++;
            }
            else {
              $sql .= sprintf(' AND :%s',$coord);
              $i = 0;
            }
          }
        }
        elseif($key == 'habitat') {
          $sql .= ' AND `habitat` LIKE :habitat';
        }
        elseif($key == 'country') {
          $sql .= ' AND `country` LIKE :country';
        }
        elseif($key == 'typeStatus') {
          $sql .= ' AND `typeStatus` LIKE :typeStatus';
        }
        elseif($key == 'fieldNumber') {
          $sql .= ' AND `fieldNumber` LIKE :fieldNumber';
        }
        else {
          $sql .= sprintf(' AND `%s` = :%s',$key,$key);
        }
      }

      if(isset($limits['georeferenced']) && $limits['georeferenced'] == 1) {
        $sql .= " AND decimalLatitude IS NOT NULL";
      }

      $sqlLim ='';
      if(isset($limits) && !empty($limits)) {
        if(isset($limits['limit'])) {

          $limit = $limits['limit'];
          $sqlLim .= " LIMIT $limit";
          if(isset($limits['offset'])) {
            $offset = $limits['offset'];
            $sqlLim .= " OFFSET $offset";
          }
        }
      }

      $paramStr['limit'] = $sqlLim;
      $paramStr['nolimit'] = $sql;
      $paramStr['params'] = $params;

      return $paramStr;

    }

    public function getSpecimens($arguments) {

      //validate arg for available field names
      $args = array();
      foreach($arguments AS $arg => $val) {
        if(in_array($arg,$this->validArguments)) {
          $args[$arg] = $val;
        }
      }

      $args = $this->validCharacters($args);

      $sql_const = $this->prepareArguments($args);
      $args = $sql_const['args'];
      if(isset($sql_const['limits'])) $limits = $sql_const['limits'];

      $sql = "SELECT occurrenceId,
                     catalogNumber,
                     family,
                     subfamily,
                     genus,
                     specificEpithet,
                     scientific_name,
                     typeStatus,
                     stateProvince,
                     country,
                     decimalLatitude,
                     decimalLongitude,
                     dateIdentified,
                     habitat,
                     minimumElevationInMeters,
                     datecollected,
                     fossil,
                     image_count,
                     fieldNumber

                     FROM darwin_core_3 WHERE 1";

      $params = array();
      foreach($args AS $key => $val) {
        if(is_array($val)) {
          foreach($val AS $k => $v) {
            $params[$k] = $v;
          }
        }
        else {
          $params[$key] = $val;
        }
      }

      foreach($args AS $key => $val) {
        if($key == 'min_date') {
          $mind = 'dateIdentified';
          $sql .= sprintf(' AND `%s` >= :%s',$mind,$key);
        }
        elseif($key == 'max_date') {
          $maxd = 'dateIdentified';
          $sql .= sprintf(' AND `%s` <= :%s',$maxd,$key);
        }
        elseif($key == 'date_collected_min') {
          $dcmin = 'datecollected';
          $sql .= sprintf(' AND `%s` >= :%s',$dcmin,$key);
        }
        elseif($key == 'date_collected_max') {
          $dcmax = 'datecollected';
          $sql .= sprintf(' AND `%s` <= :%s',$dcmax,$key);
        }
        elseif($key == 'min_elevation') {
          $mine = 'minimumElevationInMeters';
          $sql .= sprintf(' AND `%s` >= :%s',$mine,$key);
        }
        elseif($key == 'max_elevation') {
          $maxe = 'minimumElevationInMeters';
          $sql .= sprintf(' AND `%s` <= :%s',$maxe,$key);
        }
        elseif($key == 'xcoord') {
          $sql .= ' AND decimalLatitude BETWEEN';
          $i = 0;
          foreach($val AS $coord => $val) {
            if($i == 0) {
              $sql .= sprintf(' :%s',$coord);
              $i++;
            }
            else {
              $sql .= sprintf(' AND :%s',$coord);
              $i = 0;
            }
          }
        }
        elseif($key == 'ycoord') {
          $sql .= ' AND decimalLongitude BETWEEN';
          $i = 0;
          foreach($val AS $coord => $val) {
            if($i == 0) {
              $sql .= sprintf(' :%s',$coord);
              $i++;
            }
            else {
              $sql .= sprintf(' AND :%s',$coord);
              $i = 0;
            }
          }
        }
        elseif($key == 'habitat') {
          $sql .= ' AND `habitat` LIKE :habitat';
        }
        elseif($key == 'typeStatus') {
          $sql .= ' AND `typeStatus` LIKE :typeStatus';
        }
        elseif($key == 'fieldNumber') {
          $sql .= ' AND `fieldNumber` LIKE :fieldNumber';
        }
        else {
          $sql .= sprintf(' AND `%s` = :%s',$key,$key);
        }
      }

      if(isset($limits['georeferenced']) && $limits['georeferenced'] == 1) {
        $sql .= " AND decimalLatitude IS NOT NULL";
      }

      $sqlLim = $sql;
      if(isset($limits) && !empty($limits)) {
        if(isset($limits['limit'])) {

          $limit = $limits['limit'];
          $sqlLim .= " LIMIT $limit";
          if(isset($limits['offset'])) {
            $offset = $limits['offset'];
            $sqlLim .= " OFFSET $offset";
          }
        }
      }

      $stmt = $this->_db->prepare($sql);
      $stmtLim = $this->_db->prepare($sqlLim);

      foreach ($params as $key => $val) {
        // Using bindValue because bindParam binds a reference, which is
        // only evaluated at the point of execute
        $stmt->bindValue(':'.$key, $val);
        $stmtLim->bindValue(':'.$key, $val);
      }

      $stmt->execute();
      $stmtLim->execute();

      $totalRex = $stmt->rowCount();

      if($stmtLim->rowCount() > 0) {
        $specimens = $stmtLim->fetchAll(PDO::FETCH_ASSOC);

        foreach($specimens AS &$s) {
          if(!is_null($s['decimalLatitude'])) {
            $geojson = array(
              'type' => 'point',
              'coord' => array(
                  $s['decimalLatitude'],
                  $s['decimalLongitude']
                )
            );

            unset($s['decimalLongitude']);
            unset($s['decimalLatitude']);

            $s['geojson'] = $geojson;

          }

          $url = 'http://antweb.org/api/v2/?occurrenceId=' . $s['occurrenceId'];
          $s = array('url' => $url) + $s;
          unset($s['occurrenceId']);

        }

        $i = 0;
        foreach($specimens AS &$s) {
          $code = $s['catalogNumber'];
          //$code = preg_replace('/-/','',$code);
          if($this->getImages($code)) {
             $s['images'] = $this->getImages($code);
          }
        }
      }
      else {
        $specimens = array('empty_set' => 'No records found.');
        //http_response_code(204);
      }

      $results['count'] = $totalRex;

      if(isset($limit)) $results['limit'] = $limit;
      if(isset($offset)) $results['offset'] = $offset;

      $results['specimens'] = $this->utf8Scrub($specimens);

      return json_encode($results);

    }

    //returns an array of distinct names of given rank
    public function getRank($arguments) {

      //validate arg for available field names
      $args = array();
      foreach($arguments AS $arg => $val) {
        if(in_array($arg,$this->validArguments)) {
          $args[$arg] = $val;
        }
      }

      $rank = $args['rank'];
      unset($args['rank']);

      $sql_const = $this->prepareArguments($args);

      $paramStrs = $this->constructParams($sql_const);

      if(in_array($rank, $this->validArguments)) {

        if($rank == 'species') {$rank = "specificEpithet"; }

        $sql = "SELECT distinct($rank) FROM darwin_core_3 WHERE 1" . $paramStrs['nolimit'] . " ORDER BY $rank ASC ";
        $noLim = $sql;
        $sql = $sql . $paramStrs['limit'];  //add offset and limits

        $sql = $this->_db->prepare($sql);
        $noLim = $this->_db->prepare($noLim);

        foreach ($paramStrs['params']['args'] as $key => $val) {

          // Using bindValue because bindParam binds a reference, which is
          // only evaluated at the point of execute
          $sql->bindValue(':'.$key, $val);
          $noLim->bindValue(':'.$key, $val);

        }

        $sql->execute();
        $noLim->execute();

        if($sql->rowCount() > 0) {
          $ranks = $sql->fetchAll(PDO::FETCH_ASSOC);

          $ranks = $this->utf8Scrub($ranks);

         // return json_encode($ranks);

        }
        else {
          $specimens = array('empty_set' => 'No records found.');
        }

       // $results['count'] = $totalRex;
        $results['count'] = $noLim->rowCount();

        foreach($args AS $key => $val) {
          $results[$key] = $val;
        }

        $results['rank'] = $rank;
        $results['specimens'] = $this->utf8Scrub($ranks);

        return json_encode($results);

      }
    }

    public function getCoord($lat,$lon,$r,$limit=FALSE,$offset=FALSE,$distinct=FALSE) {

      if( (!is_numeric($r)) || (!is_numeric($lat)) || (!is_numeric($lon)) ) {
        exit;
      }

      $sql = "SELECT
             occurrenceId,
             catalogNumber,
             family,
             subfamily,
             genus,
             specificEpithet,
             scientific_name,
             typeStatus,
             stateProvince,
             country,
             decimalLatitude,
             decimalLongitude,
             dateIdentified,
             habitat,
             minimumElevationInMeters,
             ( 6371 * acos( cos( radians(:lat) ) * cos( radians( decimalLatitude ) ) * cos( radians( decimalLongitude ) - radians(:lon) ) + sin( radians(:lat) ) * sin( radians( decimalLatitude ) ) ) ) AS distance
             FROM darwin_core_3 HAVING distance < $r ORDER BY distance";

      if(is_numeric($limit)) {
        $sqlLim = $sql;
        $sqlLim .= " LIMIT $limit";

        if(is_numeric($offset)) {
          $sqlLim .= " OFFSET $offset";
        }
      }

      $sql = $this->_db->prepare($sql);
      $sqlLim = $this->_db->prepare($sqlLim);

      $sql->execute(array(':lat' => $lat, ':lon' => $lon));
      $sqlLim->execute(array(':lat' => $lat, ':lon' => $lon));

      $totalRex = $sql->rowCount();

      if($sql->rowCount() > 0) {

        $specimens = $sql->fetchAll(PDO::FETCH_ASSOC);

        if($distinct) {
          if(in_array($distinct, $this->valid_ranks)) {
            if($distinct == 'species') { $distinct = 'specificEpithet'; }
            $distinct_specimen = array();
            $distinct_sn = array();
            foreach($specimens AS $s) {
              if(!in_array($s[$distinct], $distinct_specimen)) {
                array_push($distinct_specimen, $s[$distinct]);
                if($distinct == 'specificEpithet') {
                  array_push($distinct_sn,$s['scientific_name']);
                }
                else {
                  array_push($distinct_sn,$s[$distinct]);
                }
              }
            }
          }

          if($distinct == 'specificEpithet') { $distinct = 'species'; }

          $results['origin'] = $lat . ',' . $lon;
          $results['radius'] = $r;
          $results['count'] = count($distinct_sn);
          $results['distinct_' . $distinct] = $distinct_sn;

          return json_encode($results);
        }

        foreach($specimens AS &$s) {
          if(!is_null($s['decimalLatitude'])) {
            $geojson = array(
              'type' => 'point',
              'coord' => array(
                  $s['decimalLatitude'],
                  $s['decimalLongitude']
                )
            );

            unset($s['decimalLongitude']);
            unset($s['decimalLatitude']);

            $s['geojson'] = $geojson;

          }

          $url = 'http://antweb.org/api/v2/?catalogNumber=' . $s['catalogNumber'];
          $s = array('url' => $url) + $s;
          unset($s['occurrenceId']);

        }

        $i = 0;
        foreach($specimens AS &$s) {
          $code = $s['catalogNumber'];
          //$code = preg_replace('/-/','',$code);
          if($this->getImages($code)) {
             $s['images'] = $this->getImages($code);
          }
        }

      }

      $results['count'] = $totalRex;
      $results['origin'] = $lat . ',' . $lon;
      $results['radius'] = $r;
      if(is_numeric($limit)) { $results['limit'] = $limit; }
      if(is_numeric($offset)) { $results['offset'] = $offset; }
      if(!empty($specimens)) { $results['specimens'] = $specimens; }

      $results = $this->utf8Scrub($results);

      return json_encode($results);

    }

    public function getImagesAddedAfter($days,$img_type=FALSE) {

      $since = date('Y-m-d', strtotime("-$days days"));

      $allowed_types = array('h','d','p','l');

      if(in_array($img_type, $allowed_types)) {
        $type = $img_type;
      }

      if($type) {
        $sql = $this->_db->prepare("SELECT * FROM image WHERE upload_date>=? AND shot_type=? ORDER BY shot_number ASC");
        $sql->execute(array($since,$type));
      }
      else {
        $sql = $this->_db->prepare("SELECT * FROM image WHERE upload_date>=? ORDER BY shot_number ASC");
        $sql->execute(array($since));
      }

      if($sql->rowCount() > 0) {
        $imgs = $sql->fetchAll(PDO::FETCH_ASSOC);

        foreach($imgs AS $img) {

          $code = $img['image_of_id'];
          $type = $img['shot_type'];

          $shot_number = $img['shot_number'];


          $images[$code]['url'] = 'http://www.antweb.org/api/v2/?catalogNumber=' . $code;
          $images[$code][$shot_number]['upload_date'] = $img['upload_date'];

          $images[$code][$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_high.jpg';
          $images[$code][$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_low.jpg';
          $images[$code][$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_med.jpg';
          $images[$code][$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_thumbview.jpg';

        }

      }
      else {
        $images = NULL;
      }

      $images = $this->utf8Scrub($images);

      return json_encode($images);

    }

    public function getImages($code) {
      $imgQuery = $this->_db->prepare("SELECT uid,shot_type,upload_date,shot_number,has_tiff FROM image WHERE image_of_id=? ORDER BY shot_number ASC");
      $imgQuery->execute(array($code));

      if($imgQuery->rowCount() > 0) {
        $imgs = $imgQuery->fetchAll(PDO::FETCH_ASSOC);
        foreach($imgs AS $img) {

          $shot_number = $img['shot_number'];
          $type = $img['shot_type'];

          $images[$shot_number]['upload_date'] = $img['upload_date'];

          $images[$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_high.jpg';
          $images[$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_low.jpg';
          $images[$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_med.jpg';
          $images[$shot_number]['shot_types'][$type]['img'][] = 'http://www.antweb.org/images/' . $code . '/' . $code . '_' . $img['shot_type'] . '_' . $img['shot_number'] . '_thumbview.jpg';

        }
      }
      else {
        $images = NULL;
      }

      return $images;

    }

  }

?>

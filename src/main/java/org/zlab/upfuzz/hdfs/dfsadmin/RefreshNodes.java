package org.zlab.upfuzz.hdfs.dfsadmin;

import org.zlab.upfuzz.Parameter;
import org.zlab.upfuzz.State;
import org.zlab.upfuzz.hdfs.HdfsState;
import org.zlab.upfuzz.utils.CONSTANTSTRINGType;

public class RefreshNodes extends Dfsadmin {

    public RefreshNodes(HdfsState state) {
        super(state.subdir);

        Parameter refreshNodesCmd = new CONSTANTSTRINGType("-refreshNodes")
                .generateRandomParameter(null, null);

        params.add(refreshNodesCmd);
    }

    @Override
    public void updateState(State state) {
    }
}
